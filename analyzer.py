import os
import tkinter as tk
from tkinter import ttk, messagebox
from collections import defaultdict

# --- НАСТРОЙКИ ---
IGNORE_DIRS = {'.git', '.svn', '.venv', 'venv', 'env', 'node_modules', '__pycache__', '.idea', '.vscode'}
MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024 
COMMON_CODE_EXTS = {
    '.go', '.py', '.sh', '.yml', '.yaml', '.js', '.ts', '.html', 
    '.css', '.c', '.cpp', '.h', '.java', '.cs', '.php', '.rb', 
    '.rs', '.json', '.xml', '.sql', '.md'
}

# --- ДЛЯ ПРИКОЛА (Размеры проектов в строках кода) ---
OSS_MILESTONES = [
    (2000, "Оригинальный Тетрис (1984)"),
    (25000, "Первая версия ядра Linux (v0.01)"),
    (85000, "Компьютер Аполлона-11 (полет на Луну)"),
    (150000, "База данных Redis"),
    (300000, "Библиотека React (JS)"),
    (500000, "Фреймворк Django"),
    (1500000, "Язык Python (CPython)"),
    (35000000, "Современное ядро Linux")
]


def format_size(size_bytes):
    """Форматирует размер файла"""
    for unit in ['Б', 'КБ', 'МБ', 'ГБ']:
        if size_bytes < 1024.0:
            return f"{size_bytes:.1f} {unit}"
        size_bytes /= 1024.0
    return f"{size_bytes:.1f} ТБ"


def count_lines(filepath):
    """Считает все строки и строки с кодом (без пустых)"""
    total = 0
    non_empty = 0
    try:
        with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
            for line in f:
                total += 1
                if line.strip():
                    non_empty += 1
    except Exception:
        return 0, 0
    return total, non_empty


class CodeAnalyzerApp(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title("Анализатор исходного кода")
        self.geometry("1000x650")
        self.current_dir = os.getcwd()
        
        style = ttk.Style(self)
        if 'clam' in style.theme_names():
            style.theme_use('clam')
            
        self.found_extensions = set()
        self.setup_ui()
        self.scan_for_extensions()

    def setup_ui(self):
        main_frame = ttk.Frame(self)
        main_frame.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)
        
        dir_label = ttk.Label(
            main_frame, 
            text=f"📂 Директория: {self.current_dir}", 
            font=("Helvetica", 11, "bold")
        )
        dir_label.pack(anchor=tk.W, pady=(0, 10))
        
        content_frame = ttk.Frame(main_frame)
        content_frame.pack(fill=tk.BOTH, expand=True)
        
        # ================= ЛЕВАЯ ПАНЕЛЬ =================
        left_frame = ttk.LabelFrame(content_frame, text=" 1. Выберите расширения: ")
        left_frame.pack(side=tk.LEFT, fill=tk.Y, padx=(0, 10))
        
        list_frame = ttk.Frame(left_frame)
        list_frame.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)
        
        self.ext_listbox = tk.Listbox(
            list_frame, selectmode=tk.MULTIPLE, width=25, 
            font=("Consolas", 11), activestyle='none', exportselection=False
        )
        self.ext_listbox.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        
        scrollbar = ttk.Scrollbar(list_frame, orient=tk.VERTICAL, command=self.ext_listbox.yview)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        self.ext_listbox.config(yscrollcommand=scrollbar.set)
        
        btn_frame = ttk.Frame(left_frame)
        btn_frame.pack(fill=tk.X, padx=5, pady=5)
        
        ttk.Button(btn_frame, text="Выбрать все", command=self.select_all).pack(fill=tk.X, pady=(0, 2))
        ttk.Button(btn_frame, text="Сбросить", command=self.clear_selection).pack(fill=tk.X, pady=(0, 15))
        
        self.analyze_btn = ttk.Button(left_frame, text="▶ АНАЛИЗИРОВАТЬ", command=self.analyze)
        self.analyze_btn.pack(fill=tk.X, padx=5, pady=(0, 10), ipady=5)
        
        # ================= ПРАВАЯ ПАНЕЛЬ (ТАБЛИЦА) =================
        right_frame = ttk.LabelFrame(content_frame, text=" 2. Статистика: ")
        right_frame.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        
        columns = ('ext', 'files', 'lines', 'code_lines', 'size')
        self.tree = ttk.Treeview(right_frame, columns=columns, show='headings')
        
        self.tree.heading('ext', text='Расширение')
        self.tree.heading('files', text='Файлов')
        self.tree.heading('lines', text='Всего строк')
        self.tree.heading('code_lines', text='Строк (код)')
        self.tree.heading('size', text='Общий размер')
        
        self.tree.column('ext', width=100, anchor=tk.CENTER)
        self.tree.column('files', width=80, anchor=tk.CENTER)
        self.tree.column('lines', width=120, anchor=tk.E)
        self.tree.column('code_lines', width=120, anchor=tk.E)
        self.tree.column('size', width=120, anchor=tk.E)
        
        tree_scroll = ttk.Scrollbar(right_frame, orient=tk.VERTICAL, command=self.tree.yview)
        self.tree.configure(yscrollcommand=tree_scroll.set)
        
        self.tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True, padx=5, pady=5)
        tree_scroll.pack(side=tk.RIGHT, fill=tk.Y, pady=5)

        # ================= СТАТУС И ПРИКОЛ =================
        self.fun_fact_label = ttk.Label(
            main_frame, 
            text="Сравнение с великими появится здесь после анализа...", 
            foreground="#d2691e", # Приятный оранжево-коричневый цвет
            font=("Helvetica", 11, "bold")
        )
        self.fun_fact_label.pack(anchor=tk.W, pady=(15, 0))

        self.status_label = ttk.Label(
            main_frame, 
            text="Ожидание...", 
            foreground="grey", font=("Helvetica", 10, "italic")
        )
        self.status_label.pack(anchor=tk.W, pady=(5, 0))

    def scan_for_extensions(self):
        self.status_label.config(text="Поиск доступных расширений...", foreground="blue")
        self.update_idletasks()
        
        for root, dirs, files in os.walk(self.current_dir):
            dirs[:] = [d for d in dirs if d not in IGNORE_DIRS]
            for file in files:
                ext = os.path.splitext(file)[1].lower()
                if ext: 
                    self.found_extensions.add(ext)
                    
        self.sorted_exts = sorted(list(self.found_extensions))
        for ext in self.sorted_exts:
            self.ext_listbox.insert(tk.END, ext)
            
        for i, ext in enumerate(self.sorted_exts):
            if ext in COMMON_CODE_EXTS:
                self.ext_listbox.selection_set(i)
                
        self.status_label.config(text="Готово. Выберите нужные расширения и нажмите 'Анализировать'.", foreground="grey")

    def select_all(self):
        self.ext_listbox.selection_set(0, tk.END)

    def clear_selection(self):
        self.ext_listbox.selection_clear(0, tk.END)

    def generate_fun_fact(self, code_lines):
        """Генерирует забавный факт на основе количества написанного кода"""
        if code_lines == 0:
            return "💡 Прикол: Тут вообще нет кода. Линус Торвальдс неодобрительно качает головой."
            
        for i, (milestone_lines, name) in enumerate(OSS_MILESTONES):
            if code_lines < milestone_lines:
                percent = (code_lines / milestone_lines) * 100
                if i == 0:
                    return f"💡 Прикол: Ваш код — это {percent:.1f}% от размера «{name}» ({milestone_lines:,} строк). Начало положено!"
                else:
                    prev_name = OSS_MILESTONES[i-1][1]
                    return f"💡 Прикол: Вы обогнали «{prev_name}»! Сейчас ваш код составляет {percent:.1f}% от «{name}» ({milestone_lines:,} строк)."
                    
        return f"🤯 Ого! Ваш проект больше, чем {OSS_MILESTONES[-1][1]}! У вас точно все хорошо с нервами?"

    def analyze(self):
        selected_indices = self.ext_listbox.curselection()
        if not selected_indices:
            messagebox.showwarning("Внимание", "Выберите хотя бы одно расширение в списке слева!")
            return
            
        selected_exts = {self.sorted_exts[i] for i in selected_indices}
        
        for item in self.tree.get_children():
            self.tree.delete(item)
            
        self.status_label.config(text="Идет анализ файлов... Это может занять время.", foreground="blue")
        self.fun_fact_label.config(text="")
        self.analyze_btn.config(state=tk.DISABLED)
        self.update_idletasks() 
        
        stats = defaultdict(lambda: {'files': 0, 'total_lines': 0, 'non_empty_lines': 0, 'size': 0})
        total_code_lines = 0 # Сюда сохраним итог для прикола
        
        for root, dirs, files in os.walk(self.current_dir):
            dirs[:] = [d for d in dirs if d not in IGNORE_DIRS]
            for file in files:
                ext = os.path.splitext(file)[1].lower()
                
                if ext in selected_exts:
                    filepath = os.path.join(root, file)
                    try:
                        size = os.path.getsize(filepath)
                        if 0 < size <= MAX_FILE_SIZE_BYTES:
                            total, code = count_lines(filepath)
                            if total > 0:
                                stats[ext]['files'] += 1
                                stats[ext]['total_lines'] += total
                                stats[ext]['non_empty_lines'] += code
                                stats[ext]['size'] += size
                                total_code_lines += code
                    except OSError:
                        continue
                        
        self.populate_table(stats)
        
        # Обновляем прикол и статус
        fun_fact = self.generate_fun_fact(total_code_lines)
        self.fun_fact_label.config(text=fun_fact)
        
        self.status_label.config(text="✅ Анализ успешно завершен!", foreground="green")
        self.analyze_btn.config(state=tk.NORMAL)
        
    def populate_table(self, stats):
        total_files = total_lines = total_code = total_size = 0
        
        for ext, data in sorted(stats.items()):
            total_files += data['files']
            total_lines += data['total_lines']
            total_code += data['non_empty_lines']
            total_size += data['size']
            
            self.tree.insert('', tk.END, values=(
                ext,
                f"{data['files']:,}".replace(',', ' '),
                f"{data['total_lines']:,}".replace(',', ' '),
                f"{data['non_empty_lines']:,}".replace(',', ' '),
                format_size(data['size'])
            ))
            
        if stats:
            self.tree.insert('', tk.END, values=('-'*15, '-'*10, '-'*15, '-'*15, '-'*15))
            self.tree.insert('', tk.END, values=(
                "ИТОГО",
                f"{total_files:,}".replace(',', ' '),
                f"{total_lines:,}".replace(',', ' '),
                f"{total_code:,}".replace(',', ' '),
                format_size(total_size)
            ))


if __name__ == "__main__":
    app = CodeAnalyzerApp()
    app.mainloop()