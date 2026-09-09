# VisorLink CDN Server Upgrade Specification (v2)
## Стек: Python (WSGI / Flask) + Waitress + Cloudflare Zero Trust (cloudflared)

Данная спецификация описывает практическую реализацию на вашем стеке:
- **Шлюз / Туннель:** Cloudflare Zero Trust (`cloudflared` tunnel).
- **WSGI-сервер:** Waitress.
- **Бэкенд:** Python (Flask / WSGI / Firebase Admin SDK).

---

## 1. Отправка чанками (Chunked / Resumable Upload) — Рекомендуемое решение

### Почему для вашего стека чанки — это ЕДИНСТВЕННЫЙ надёжный путь:
1. **Жесткий лимит Cloudflare (HTTP 413):**
   - Cloudflare Free / Pro блокирует запросы с телом > **100 MB**, Business > **200 MB**. Попытка отправить видео 150 МБ одним куском вызовет ошибку `413 Request Entity Too Large` на серверах Cloudflare ещё до того, как запрос дойдёт до вашего туннеля `cloudflared`.
   - При чанках (размер блока **5–10 МБ**) лимиты Cloudflare никогда не сработают — можно передавать файлы любого размера (до 2–4 ГБ).
2. **Точный прогресс без зависаний:**
   - Клиент отправляет чанк 5 МБ -> сервер подтверждает запись -> прогресс сдвигается на точные проценты. Нет проблемы, когда клиент отдал данные в сокет, а сервер ещё 20 секунд их переваривает.
3. **Отмена на лету и докачка при сбоях (Resumable):**
   - **Отмена:** клиент шлёт `DELETE /upload/chunked/{upload_id}` — сервер мгновенно удаляет склеенный `.part` файл.
   - **Сбой связи (лифт, метро, переключение Wi-Fi/LTE):** клиент запрашивает `GET /upload/chunked/{upload_id}`, узнаёт, сколько байт уже получено (`offset`), и продолжает заливать со следующего чанка без необходимости начинать сначала.

---

### Архитектура протокола Chunked Upload

Протокол состоит из 4 простых эндпоинтов:
- `POST /upload/chunked/init` — инициализация сессии.
- `POST /upload/chunked/{upload_id}` — отправка очередного чанка.
- `GET /upload/chunked/{upload_id}` — проверка статуса (сколько байт получено).
- `DELETE /upload/chunked/{upload_id}` — отмена загрузки и очистка.

#### 1. Инициализация: `POST /upload/chunked/init`
- **Headers:** `Authorization: Bearer <firebase_token>`
- **Body (JSON):**
  ```json
  {
    "file_name": "video_trip.mp4",
    "file_size": 154200000,
    "mime_type": "video/mp4",
    "total_chunks": 30,
    "chunk_size": 5242880,
    "zone": "public"
  }
  ```
- **Response (201 Created):**
  ```json
  {
    "upload_id": "up_7f9a12c8e3",
    "chunk_size": 5242880,
    "expected_chunks": 30
  }
  ```

#### 2. Загрузка чанка: `POST /upload/chunked/{upload_id}`
- **Headers:**
  - `X-Chunk-Index: 0` (0-indexed: 0, 1, 2...)
  - `X-Chunk-Offset: 0` (байт начала чанка)
  - `Content-Type: application/octet-stream`
- **Body:** сырые бинарные байты чанка (5 МБ).
- **Response (200 OK):**
  ```json
  {
    "upload_id": "up_7f9a12c8e3",
    "chunk_index": 0,
    "bytes_received": 5242880,
    "is_completed": false
  }
  ```
- **Когда передан последний чанк (`chunk_index == total_chunks - 1`):**
  Сервер финализирует файл, запускает FFmpeg для видео (если нужно) и возвращает статус:
  ```json
  {
    "upload_id": "up_7f9a12c8e3",
    "is_completed": true,
    "media_id": "vid_9a8fbc71",
    "original_name": "video_trip.mp4",
    "size": 154200000,
    "duration": 48,
    "width": 1920,
    "height": 1080,
    "thumb_id": "thm_vid_9a8fbc71",
    "thumb_url": "https://api.visorlink.org/f/thm_vid_9a8fbc71"
  }
  ```

#### 3. Отмена: `DELETE /upload/chunked/{upload_id}`
- **Response (204 No Content)**
- Сервер немедленно удаляет временный файл `/tmp/uploads/{upload_id}.part` и запись из БД/кэша.

#### 4. Статус / Возобновление: `GET /upload/chunked/{upload_id}`
- **Response (200 OK):**
  ```json
  {
    "upload_id": "up_7f9a12c8e3",
    "bytes_received": 26214400,
    "next_chunk_index": 5,
    "is_completed": false
  }
  ```

---

### Реализация на Python / Flask

```python
import os
import uuid
import json
from flask import request, jsonify

UPLOAD_TEMP_DIR = "/tmp/visorlink_chunks"
os.makedirs(UPLOAD_TEMP_DIR, exist_ok=True)

# 1. Инициализация сессии
@app.route('/upload/chunked/init', methods=['POST'])
def init_chunked_upload():
    token = request.headers.get('Authorization', '').replace('Bearer ', '')
    user = verify_firebase_token(token)
    
    data = request.json or {}
    upload_id = f"up_{uuid.uuid4().hex[:16]}"
    
    session_data = {
        "upload_id": upload_id,
        "uid": user['uid'],
        "file_name": data.get('file_name', 'file'),
        "file_size": data.get('file_size', 0),
        "mime_type": data.get('mime_type', 'application/octet-stream'),
        "total_chunks": data.get('total_chunks', 1),
        "chunk_size": data.get('chunk_size', 5242880),
        "zone": data.get('zone', 'public'),
        "bytes_received": 0,
        "chunks_received": 0
    }
    
    # Сохраняем метаданные сессии (в Redis, SQLite или JSON-файл)
    save_session(upload_id, session_data)
    
    # Создаем пустой файл под сборку
    part_path = os.path.join(UPLOAD_TEMP_DIR, f"{upload_id}.part")
    with open(part_path, "wb") as f:
        pass
        
    return jsonify({"upload_id": upload_id, "chunk_size": session_data["chunk_size"]}), 201

# 2. Прием чанка
@app.route('/upload/chunked/<upload_id>', methods=['POST'])
def upload_chunk(upload_id):
    session = get_session(upload_id)
    if not session:
        return jsonify({"error": "Upload session not found"}), 404
        
    chunk_index = int(request.headers.get('X-Chunk-Index', -1))
    part_path = os.path.join(UPLOAD_TEMP_DIR, f"{upload_id}.part")
    
    # Считываем сырое тело чанка
    chunk_bytes = request.get_data()
    if not chunk_bytes:
        return jsonify({"error": "Empty chunk"}), 400
        
    # Дописываем чанк в файл
    with open(part_path, "ab") as f:
        f.write(chunk_bytes)
        
    session["bytes_received"] += len(chunk_bytes)
    session["chunks_received"] += 1
    save_session(upload_id, session)
    
    # Проверяем, завершена ли загрузка
    is_completed = session["chunks_received"] >= session["total_chunks"] or session["bytes_received"] >= session["file_size"]
    
    if not is_completed:
        return jsonify({
            "upload_id": upload_id,
            "chunk_index": chunk_index,
            "bytes_received": session["bytes_received"],
            "is_completed": False
        }), 200
        
    # ФИНОБРАБОТКА ПОСЛЕ ПОСЛЕДНЕГО ЧАНКА
    final_path = os.path.join(UPLOAD_TEMP_DIR, f"{upload_id}_{session['file_name']}")
    os.rename(part_path, final_path)
    
    # Если это видео — запускаем FFmpeg для генерации превью и длительности
    video_meta = {}
    if session["mime_type"].startswith("video/"):
        thumb_path = os.path.join(UPLOAD_TEMP_DIR, f"{upload_id}_thumb.jpg")
        video_meta = process_video_file(final_path, thumb_path)
        # Сохраняем превью в хранилище
        thumb_id = storage.save_file(thumb_path, session["uid"], "image/jpeg")
        video_meta["thumb_id"] = thumb_id
        video_meta["thumb_url"] = f"https://api.visorlink.org/f/{thumb_id}"
        if os.path.exists(thumb_path): os.remove(thumb_path)
        
    # Перемещаем основной файл в хранилище CDN
    media_id = storage.save_file(final_path, session["uid"], session["mime_type"])
    if os.path.exists(final_path): os.remove(final_path)
    delete_session(upload_id)
    
    result = {
        "upload_id": upload_id,
        "is_completed": True,
        "media_id": media_id,
        "original_name": session["file_name"],
        "size": session["bytes_received"],
        "mime_type": session["mime_type"]
    }
    result.update(video_meta)
    return jsonify(result), 200

# 3. Отмена загрузки
@app.route('/upload/chunked/<upload_id>', methods=['DELETE'])
def abort_chunked_upload(upload_id):
    part_path = os.path.join(UPLOAD_TEMP_DIR, f"{upload_id}.part")
    if os.path.exists(part_path):
        os.remove(part_path)
    delete_session(upload_id)
    return "", 204

---

## 2. Автоматическая генерация превьюшек для видео (Video Thumbnails)

### Требуемые зависимости в Python
Установить системный `ffmpeg` на сервере:
```bash
sudo apt update && sudo apt install -y ffmpeg
pip install Pillow
```

### Реализация в Python (генератор превью и метаданных)
Создаем утилиту обработки видео `video_processor.py`:
```python
import subprocess
import json
import os

def process_video_file(video_path: str, output_thumb_path: str) -> dict:
    """
    Извлекает кадр на 1-й секунде, сжимает его в JPEG/WebP (макс 720p)
    и считывает точные параметры: width, height, duration.
    """
    # 1. Извлекаем метаданные через ffprobe
    cmd_probe = [
        "ffprobe", "-v", "error",
        "-show_entries", "format=duration:stream=width,height,codec_type",
        "-of", "json", video_path
    ]
    meta = {}
    try:
        result = subprocess.run(cmd_probe, capture_output=True, text=True, timeout=10)
        probe_data = json.loads(result.stdout)
        
        duration = int(float(probe_data.get('format', {}).get('duration', 0)))
        meta['duration'] = duration

        for stream in probe_data.get('streams', []):
            if stream.get('codec_type') == 'video':
                meta['width'] = int(stream.get('width', 0))
                meta['height'] = int(stream.get('height', 0))
                break
    except Exception as e:
        meta['duration'] = 0
        meta['width'] = 0
        meta['height'] = 0

    # 2. Извлекаем постер на 1-й секунде с масштабированием до макс 720px по ширине
    # Качество q:v 2 (высокое качество JPEG при малом весе)
    seek_time = "00:00:01.000" if meta['duration'] > 1 else "00:00:00.000"
    cmd_thumb = [
        "ffmpeg", "-y",
        "-ss", seek_time,
        "-i", video_path,
        "-vframes", "1",
        "-vf", "scale='min(720,iw)':-2",
        "-q:v", "2",
        output_thumb_path
    ]
    subprocess.run(cmd_thumb, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=15)
    
    return meta
```

### Обновление эндпоинта `/upload`
Когда на сервер загружается `mime_type.startswith('video/')`:
1. Вызываем `process_video_file(temp_video_path, temp_thumb_path)`.
2. Сохраняем превью как связанный медиафайл (например, `thumb_id = f"thm_{media_id}"`).
3. Возвращаем клиенту расширенный JSON:
   ```json
   {
     "media_id": "vid_9a8fbc71",
     "original_name": "vacation.mp4",
     "mime_type": "video/mp4",
     "size": 34102931,
     "width": 1920,
     "height": 1080,
     "duration": 34,
     "thumb_id": "thm_vid_9a8fbc71",
     "thumb_url": "https://api.visorlink.org/f/thm_vid_9a8fbc71"
   }
   ```
4. **В Android приложении:**
   - Клиент сохраняет `thumbUrl` и `duration` сразу в Firestore сообщение без выполнения локального `MediaMetadataRetriever`!

---

## 3. Прогресс загрузки с сервера и стриминг (Download Progress & HTTP 206)

### Проблема отсутствия Content-Length и перемотки
Если в Flask отдавать файл через `Response(stream())`, Waitress отправляет заголовок `Transfer-Encoding: chunked` и **НЕ ОТДАЕТ `Content-Length`**.
В результате:
- Клиент Android получает `response.body().contentLength() == -1` -> невозможно отобразить шкалу прогресса `0%..100%`.
- ExoPlayer не может делать seek (перемотку) по видео/аудио.

### Правильная реализация отдачи медиафайлов в Flask с поддержкой Range-запросов
Для Flask существует стандартная функция `send_file`, в которой нужно **обязательно включить параметр `conditional=True`**:

```python
import os
from flask import send_file, request, abort, make_response

@app.route('/f/<media_id>', methods=['GET'])
@app.route('/p/<media_id>', methods=['GET'])
def get_media_file(media_id):
    # 1. Авторизация (для /f/ проверяем token в query или Bearer, для /p/ публично)
    if request.path.startswith('/f/'):
        token = request.args.get('token') or request.headers.get('Authorization', '').replace('Bearer ', '')
        if not verify_token(token):
            abort(401)

    file_record = db.get_media_record(media_id)
    if not file_record:
        abort(404)

    file_path = file_record['local_path']
    file_size = os.path.getsize(file_path)

    # 2. conditional=True ОБЯЗАТЕЛЕН:
    # Flask сам корректно обрабатывает Range: bytes=0-1048576,
    # выставляет заголовок Accept-Ranges: bytes,
    # отдает HTTP 206 Partial Content и ТОЧНЫЙ Content-Length!
    response = send_file(
        file_path,
        mimetype=file_record['mime_type'],
        conditional=True,
        download_name=file_record.get('original_name', 'file'),
        as_attachment=False
    )
    
    # Добавляем строгие заголовки
    response.headers['Accept-Ranges'] = 'bytes'
    response.headers['ETag'] = file_record.get('hash', media_id)
    response.headers['Cache-Control'] = 'public, max-age=86400'
    return response
```

### Настройка Cloudflare Zero Trust для видео/аудио
В панели Cloudflare (Dashboard):
1. Убедитесь, что для путей `/f/*` и `/p/*` не включена агрессивная минификация / сжатие (Brotli/Gzip для медиа).
2. Cloudflare Tunnel (`cloudflared`) автоматически пробрасывает Range-запросы и `206 Partial Content` на бэкенд Waitress.

---

## 4. Перенос отправки альбомов с Cloud Function на CDN (Python)

### Почему это необходимо
Сейчас клиент:
1. Загружает фотографии по одной через `POST /upload`.
2. Вызывает Google Cloud Function `sendAlbum`, страдающую от cold starts (до 3–5 секунд ожидания перед отправкой сообщения).

### Решение: Прямая отправка альбома через Python Backend

Вместо Cloud Functions используем официальный **`firebase-admin` Python SDK** на вашем сервере:
```bash
pip install firebase-admin
```

Инициализация в Python (один раз при старте приложения):
```python
import firebase_admin
from firebase_admin import credentials, firestore, auth

cred = credentials.Certificate("serviceAccountKey.json") # ключ из консоли Firebase
firebase_admin.initialize_app(cred)
firestore_db = firestore.client()
```

#### Эндпоинт отправки альбома: `POST /chats/<chat_id>/messages/album`

```python
from flask import request, jsonify
from datetime import datetime

@app.route('/chats/<chat_id>/messages/album', methods=['POST'])
def send_album_message(chat_id):
    # 1. Валидация Firebase ID Token
    auth_header = request.headers.get('Authorization', '')
    if not auth_header.startswith('Bearer '):
        return jsonify({"error": "Unauthorized"}), 401
    
    id_token = auth_header.split(' ')[1]
    try:
        decoded_token = auth.verify_id_token(id_token)
        current_uid = decoded_token['uid']
    except Exception as e:
        return jsonify({"error": f"Invalid token: {str(e)}"}), 401

    data = request.json or {}
    images = data.get('images', []) # Список [{"cdnMediaId": "...", "fileName": "...", "spoiler": false}]
    caption = data.get('caption', '').strip() or None
    reply_to = data.get('replyTo') # dict или None

    if not images:
        return jsonify({"error": "Empty album"}), 400

    # Получаем username отправителя
    user_doc = firestore_db.collection('users').document(current_uid).get()
    sender_username = user_doc.to_dict().get('username', 'user') if user_doc.exists else 'user'

    chat_ref = firestore_db.collection('chats').document(chat_id)
    chat_doc = chat_ref.get()
    if not chat_doc.exists:
        return jsonify({"error": "Chat not found"}), 404
    
    chat_data = chat_doc.to_dict()
    participants = chat_data.get('participants', [])
    chat_type = chat_data.get('type', 'direct')

    # Расчет следующего seq (в соответствии со спецификацией синхронизации)
    current_last_seq = chat_data.get('lastSeq', 0)
    next_seq = current_last_seq + 1

    # Создаем сообщение в подколлекции messages
    msg_ref = chat_ref.collection('messages').document()
    message_id = msg_ref.id

    new_message_data = {
        "id": message_id,
        "type": "ALBUM",
        "senderId": current_uid,
        "senderUsername": sender_username,
        "images": images,
        "caption": caption,
        "replyTo": reply_to,
        "seq": next_seq,
        "createdAt": firestore.SERVER_TIMESTAMP,
        "deleted": False,
        "reactions": [],
        "readBy": [current_uid]
    }

    # Подготовка атомарного обновления документа чата
    preview_text = f"📷 [Альбом] {caption}" if caption else "📷 [Альбом]"
    
    chat_updates = {
        "lastSeq": next_seq,
        "lastMessageSenderId": current_uid,
        "lastMessageAt": firestore.SERVER_TIMESTAMP,
        "lastMessage": {
            "text": preview_text,
            "senderId": current_uid,
            "senderUsername": sender_username,
            "readBy": [current_uid],
            "createdAt": firestore.SERVER_TIMESTAMP
        }
    }

    # Для личного чата инкрементируем unreadCount собеседника
    if chat_type == "direct":
        other_uid = next((p for p in participants if p != current_uid), None)
        if other_uid:
            chat_updates[f"unreadCount.{other_uid}"] = firestore.Increment(1)

    # Атомарный коммит батча
    batch = firestore_db.batch()
    batch.set(msg_ref, new_message_data)
    batch.update(chat_ref, chat_updates)
    batch.update(firestore_db.collection('users').document(current_uid), {
        "lastMessageAt": firestore.SERVER_TIMESTAMP
    })
    batch.commit()

    return jsonify({
        "messageId": message_id,
        "seq": next_seq,
        "status": "sent"
    }), 200
```

---

## 5. Чеклист изменений для Python-сервера

1. **Waitress:** В функции `serve(...)` выставить `max_request_body_size=524288000` (500 МБ) и `channel_timeout=120`.
2. **Отмена отправки:** В эндпоинте `/upload` добавить удаление временного файла в блоке `finally:` и обработку исключений сокета (`ConnectionResetError`).
3. **FFmpeg для видео:**
   - Установить `ffmpeg` в ОС (`apt install ffmpeg`).
   - При загрузке файла `video/*` генерировать превью кадра через `ffmpeg -ss 00:00:01 -i ... -vf "scale='min(720,iw)':-2" -q:v 2 thumb.jpg`.
   - Возвращать в ответе `POST /upload` поля `thumb_id`, `thumb_url`, `width`, `height`, `duration`.
4. **Отдача с Content-Length:** Заменить кастомную потоковую отдачу файлов на `flask.send_file(..., conditional=True)`. Убедиться, что клиенту отдаются заголовки `Content-Length` и `Accept-Ranges: bytes`.
5. **Отправка альбомов:** Создать эндпоинт `POST /chats/<chat_id>/messages/album` на основе `firebase-admin`, который атомарно создает сообщение и обновляет `lastSeq` и `lastMessage`, полностью заменяя Cloud Function.
