package org.visorlink.app.data.idcard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Сверка порта с вебом: ожидаемые значения посчитаны исходным src/utils/idCardModel.js
 * (node). Если тест упал — карта на Android выглядит не так, как в вебе.
 */
class IdCardGeneratorTest {

    @Test
    fun `seed 1 совпадает с вебом`() {
        val traits = IdCardGenerator.generateTraits(1L)
        assertEquals(1L, traits.seed)
        assertEquals(IdFinish.PEARL, traits.finish)
        assertEquals(true, traits.laminated)
        assertEquals(1, traits.wear)
        assertEquals(IdFoil.GALAXY, traits.foil)
        assertEquals(11, traits.variant)
        assertEquals(-0.7, traits.tilt, 0.0)
        assertEquals(IdEdition.EPIC, traits.edition)
        assertEquals("VL-44CX-3AKB", IdCardGenerator.generateSerial(1L))
        assertEquals("3B0BE4:CD1A49:DF3CF2:720FD8", IdCardGenerator.authKey(1L))
        assertEquals("M3.9 23.0L3.3 22.6L3.0 21.5L3.2 19.6L3.9 17.3L5.0 14.7L6.6 11.9L8.5 9.2L10.7 6.9L13.0 5.1L15.2 3.9L17.2 3.5L18.9 3.9L20.3 5.1L21.2 6.9L21.6 9.2L21.5 11.9L20.9 14.7L20.1 17.3L19.0 19.6L17.8 21.5L16.7 22.6L15.7 23.0L15.7 23.0L15.3 22.9L15.0 22.5L14.9 21.9L15.1 21.2L15.5 20.4L16.2 19.5L17.0 18.7L18.1 18.0L19.3 17.4L20.5 17.0L21.6 16.9L22.7 17.0L23.7 17.4L24.4 18.0L24.9 18.7L25.1 19.5L25.1 20.4L24.9 21.2L24.5 21.9L24.0 22.5L23.5 22.9L23.0 23.0L23.0 23.0L22.7 22.8L22.6 22.4L22.7 21.7L23.0 20.7L23.6 19.7L24.4 18.6L25.4 17.5L26.5 16.6L27.7 15.9L28.9 15.4L30.1 15.3L31.1 15.4L32.0 15.9L32.7 16.6L33.1 17.5L33.3 18.6L33.3 19.7L33.0 20.7L32.7 21.7L32.2 22.4L31.8 22.8L31.4 23.0L31.4 23.0L31.3 22.7L31.6 21.9L32.0 20.7L32.8 19.0L33.7 17.2L34.8 15.3L36.0 13.4L37.3 11.8L38.5 10.5L39.7 9.7L40.6 9.5L41.4 9.7L41.9 10.5L42.2 11.8L42.2 13.4L42.0 15.3L41.7 17.2L41.2 19.0L40.7 20.7L40.2 21.9L39.8 22.7L39.6 23.0L39.6 23.0L39.5 22.8L39.5 22.1L39.8 21.0L40.3 19.7L41.0 18.1L41.9 16.5L42.9 15.0L44.0 13.6L45.1 12.6L46.1 11.9L47.0 11.7L47.7 11.9L48.2 12.6L48.5 13.6L48.6 15.0L48.4 16.5L48.1 18.1L47.6 19.7L47.1 21.0L46.6 22.1L46.1 22.8L45.8 23.0L45.8 23.0L45.3 22.9L45.0 22.5L44.8 22.0L45.0 21.3L45.3 20.5L46.0 19.7L46.9 18.9L48.0 18.2L49.2 17.7L50.5 17.3L51.7 17.2L52.9 17.3L53.9 17.7L54.7 18.2L55.3 18.9L55.6 19.7L55.6 20.5L55.4 21.3L55.0 22.0L54.4 22.5L53.8 22.9L53.2 23.0L53.2 23.0L52.7 22.9L52.4 22.6L52.2 22.1L52.3 21.4L52.6 20.6L53.2 19.9L54.0 19.1L54.9 18.4L56.0 17.9L57.1 17.6L58.2 17.5L59.2 17.6L60.1 17.9L60.8 18.4L61.2 19.1L61.4 19.9L61.4 20.6L61.1 21.4L60.7 22.1L60.2 22.6L59.6 22.9L59.0 23.0L59.0 23.0L58.6 22.9L58.4 22.6L58.3 22.0L58.5 21.4L58.9 20.6L59.4 19.8L60.2 19.1L61.1 18.4L62.1 17.9L63.1 17.5L64.2 17.4L65.1 17.5L65.9 17.9L66.5 18.4L66.9 19.1L67.1 19.8L67.0 20.6L66.8 21.4L66.5 22.0L66.0 22.6L65.5 22.9L65.1 23.0L64.0 24.8L62.8 25.5L61.6 26.2L60.2 26.8L58.7 27.3L57.0 27.6L55.0 27.8L52.9 27.8L50.5 27.6L47.9 27.2L45.0 26.7L42.0 26.1L38.8 25.3L35.5 24.4L32.1 23.5L28.7 22.5", IdCardGenerator.signaturePath(1L))

        val d = IdCardGenerator.deriveDetails(1L)
        assertEquals(IdCardGenerator.Guilloche(702, 370, 14, 3, 16, 0.26754270878620445, 93), d.guilloche)
        assertEquals(IdCardGenerator.Waves(2.045375982299447, 13.492006342858076, 5.611187122935018), d.waves)
        assertEquals(IdCardGenerator.HoloShape.STAR, d.holoShape)
        assertEquals(IdCardGenerator.HoloSpot.CORNER_TILT, d.holoSpot)
        assertEquals(0, d.stampStyle)
        assertEquals(-14, d.stampRot)
        assertEquals(0.514680946804583, d.stampDx, 0.0)
        assertEquals(0.138959772605449, d.stampDy, 0.0)
        assertEquals(0.7997492636647076, d.stampInk, 0.0)
        assertEquals(320, d.stampPress)
        assertEquals(false, d.secondStamp)
        assertEquals(5, d.secondRot)
        assertEquals(5, d.led)
        assertEquals(1, d.coat)
        assertEquals(0, d.chipCorner)
        assertEquals(IdCardGenerator.Smudge(34, 64, 16), d.smudge)
        assertEquals(null, d.bubble)
        assertEquals(IdCardGenerator.Misprint(-0.178858699509874, 0.0027748707681894413), d.misprint)
        assertEquals(789935719L, d.artSeed)

        val scratches = IdCardGenerator.scratches(1L, 3)
        assertEquals(IdCardGenerator.Scratch(23.28055538237095, 38.07732621207833, 20.648416723039933, 42.275073274898965, 0.32264233209425586), scratches[0])
        assertEquals(IdCardGenerator.Scratch(45.75721381697804, 82.92276619467884, 39.86632120832405, 90.46298009683244, 0.2203630475210957), scratches[1])
        assertEquals(IdCardGenerator.Scratch(23.98887909948826, 14.835345302708447, 20.285380796673984, 21.591729885116454, 0.3070330444374122), scratches[2])
    }

    @Test
    fun `seed 42 совпадает с вебом`() {
        val traits = IdCardGenerator.generateTraits(42L)
        assertEquals(42L, traits.seed)
        assertEquals(IdFinish.PEARL, traits.finish)
        assertEquals(true, traits.laminated)
        assertEquals(2, traits.wear)
        assertEquals(IdFoil.PRISM, traits.foil)
        assertEquals(2, traits.variant)
        assertEquals(0.1, traits.tilt, 0.0)
        assertEquals(IdEdition.UNCOMMON, traits.edition)
        assertEquals("VL-ACS2-6WMF", IdCardGenerator.generateSerial(42L))
        assertEquals("F33218:9A2E50:30F4DD:76BC25", IdCardGenerator.authKey(42L))
        assertEquals("M5.1 23.0L3.9 22.6L3.2 21.5L3.0 19.8L3.3 17.5L4.2 15.0L5.7 12.3L7.5 9.7L9.7 7.5L12.1 5.8L14.5 4.6L16.8 4.3L18.7 4.6L20.3 5.8L21.3 7.5L21.8 9.7L21.8 12.3L21.2 15.0L20.1 17.5L18.7 19.8L17.2 21.5L15.6 22.6L14.2 23.0L14.2 23.0L14.0 22.7L14.0 21.9L14.3 20.7L14.9 19.1L15.7 17.3L16.8 15.4L18.1 13.6L19.5 12.0L20.9 10.8L22.2 10.0L23.4 9.7L24.4 10.0L25.1 10.8L25.6 12.0L25.8 13.6L25.6 15.4L25.3 17.3L24.8 19.1L24.2 20.7L23.5 21.9L23.0 22.7L22.5 23.0L22.5 23.0L22.0 22.9L21.6 22.5L21.5 21.8L21.6 21.0L22.0 20.1L22.8 19.1L23.7 18.2L24.9 17.3L26.2 16.7L27.5 16.3L28.8 16.2L30.1 16.3L31.1 16.7L31.9 17.3L32.5 18.2L32.7 19.1L32.7 20.1L32.4 21.0L31.9 21.8L31.3 22.5L30.6 22.9L30.0 23.0L30.0 23.0L29.9 22.7L30.2 22.0L30.6 20.8L31.3 19.3L32.2 17.6L33.2 15.8L34.3 14.1L35.5 12.6L36.7 11.4L37.8 10.7L38.7 10.4L39.4 10.7L39.9 11.4L40.2 12.6L40.3 14.1L40.1 15.8L39.8 17.6L39.4 19.3L38.9 20.8L38.4 22.0L38.1 22.7L37.9 23.0L37.9 23.0L37.8 22.9L37.8 22.4L38.1 21.7L38.5 20.9L39.1 19.9L39.8 18.8L40.6 17.8L41.5 16.9L42.5 16.2L43.4 15.8L44.3 15.7L45.0 15.8L45.6 16.2L46.1 16.9L46.3 17.8L46.4 18.8L46.3 19.9L46.1 20.9L45.9 21.7L45.6 22.4L45.3 22.9L45.1 23.0L45.1 23.0L44.6 22.9L44.2 22.5L44.1 21.8L44.2 21.0L44.6 20.1L45.3 19.1L46.1 18.2L47.2 17.3L48.3 16.7L49.5 16.3L50.7 16.2L51.8 16.3L52.7 16.7L53.4 17.3L53.8 18.2L53.9 19.1L53.8 20.1L53.5 21.0L53.0 21.8L52.4 22.5L51.7 22.9L51.1 23.0L51.1 23.0L50.6 22.7L50.3 22.0L50.4 20.7L50.8 19.2L51.5 17.4L52.5 15.5L53.7 13.7L55.1 12.1L56.5 10.9L58.0 10.1L59.3 9.8L60.4 10.1L61.2 10.9L61.7 12.1L61.9 13.7L61.8 15.5L61.3 17.4L60.7 19.2L59.9 20.7L59.0 22.0L58.1 22.7L57.4 23.0L57.1 24.8L56.8 25.5L56.4 26.2L55.9 26.8L55.2 27.3L54.4 27.6L53.3 27.8L52.0 27.8L50.4 27.6L48.6 27.2L46.7 26.7L44.5 26.1L42.1 25.3L39.7 24.4L37.1 23.5L34.5 22.5", IdCardGenerator.signaturePath(42L))

        val d = IdCardGenerator.deriveDetails(42L)
        assertEquals(IdCardGenerator.Guilloche(665, 389, 16, 7, 16, 0.3043701769132167, 81), d.guilloche)
        assertEquals(IdCardGenerator.Waves(1.6739376743789762, 8.210186693817377, 3.6873429620521594), d.waves)
        assertEquals(IdCardGenerator.HoloShape.HEX, d.holoShape)
        assertEquals(IdCardGenerator.HoloSpot.CORNER_TILT, d.holoSpot)
        assertEquals(1, d.stampStyle)
        assertEquals(-7, d.stampRot)
        assertEquals(-0.14196160738356411, d.stampDx, 0.0)
        assertEquals(-0.4531698239967227, d.stampDy, 0.0)
        assertEquals(0.7702176879253239, d.stampInk, 0.0)
        assertEquals(85, d.stampPress)
        assertEquals(true, d.secondStamp)
        assertEquals(-12, d.secondRot)
        assertEquals(7, d.led)
        assertEquals(4, d.coat)
        assertEquals(3, d.chipCorner)
        assertEquals(IdCardGenerator.Smudge(23, 54, 93), d.smudge)
        assertEquals(null, d.bubble)
        assertEquals(IdCardGenerator.Misprint(-0.06897275371011347, 0.06714655896648764), d.misprint)
        assertEquals(364178403L, d.artSeed)

        val scratches = IdCardGenerator.scratches(42L, 3)
        assertEquals(IdCardGenerator.Scratch(89.60264306515455, 35.824560024775565, 91.36735998820498, 38.76122985023077, 0.26028791370335963), scratches[0])
        assertEquals(IdCardGenerator.Scratch(91.49437875021249, 67.61023106519133, 77.794687741064, 67.93151167738193, 0.37816291275667024), scratches[1])
        assertEquals(IdCardGenerator.Scratch(6.787900207564235, 19.470387184992433, 9.327815945091533, 21.634746574985424, 0.32039691000245507), scratches[2])
    }

    @Test
    fun `seed 123456789 совпадает с вебом`() {
        val traits = IdCardGenerator.generateTraits(123456789L)
        assertEquals(123456789L, traits.seed)
        assertEquals(IdFinish.BASE, traits.finish)
        assertEquals(false, traits.laminated)
        assertEquals(2, traits.wear)
        assertEquals(IdFoil.NONE, traits.foil)
        assertEquals(3, traits.variant)
        assertEquals(0.7, traits.tilt, 0.0)
        assertEquals(IdEdition.COMMON, traits.edition)
        assertEquals("VL-UHCN-QXX9", IdCardGenerator.generateSerial(123456789L))
        assertEquals("497CAB:4E81B6:213EE7:6BAFD2", IdCardGenerator.authKey(123456789L))
        assertEquals("M4.4 23.0L3.5 22.6L3.0 21.4L3.0 19.5L3.6 17.0L4.6 14.2L6.2 11.3L8.1 8.5L10.3 6.1L12.6 4.2L14.9 3.0L17.0 2.5L18.8 3.0L20.2 4.2L21.1 6.1L21.4 8.5L21.2 11.3L20.6 14.2L19.5 17.0L18.2 19.5L16.8 21.4L15.4 22.6L14.1 23.0L14.1 23.0L13.6 22.8L13.3 22.4L13.2 21.6L13.4 20.7L13.9 19.6L14.6 18.4L15.6 17.4L16.8 16.4L18.1 15.7L19.4 15.2L20.7 15.0L21.9 15.2L22.8 15.7L23.6 16.4L24.0 17.4L24.2 18.4L24.1 19.6L23.7 20.7L23.2 21.6L22.5 22.4L21.8 22.8L21.2 23.0L21.2 23.0L21.1 22.9L21.1 22.5L21.3 21.8L21.6 21.0L22.2 20.1L22.8 19.2L23.6 18.2L24.4 17.4L25.3 16.8L26.1 16.4L26.9 16.3L27.6 16.4L28.2 16.8L28.6 17.4L28.8 18.2L28.9 19.2L28.8 20.1L28.6 21.0L28.4 21.8L28.1 22.5L27.8 22.9L27.6 23.0L27.6 23.0L27.4 22.9L27.4 22.4L27.6 21.8L28.0 20.9L28.5 19.9L29.2 18.9L30.1 18.0L31.0 17.1L31.9 16.4L32.8 16.0L33.7 15.9L34.4 16.0L35.0 16.4L35.5 17.1L35.7 18.0L35.8 18.9L35.8 19.9L35.6 20.9L35.3 21.8L34.9 22.4L34.6 22.9L34.4 23.0L34.4 23.0L34.0 22.9L33.9 22.4L33.9 21.7L34.1 20.9L34.6 19.9L35.3 18.8L36.3 17.8L37.3 17.0L38.4 16.3L39.6 15.8L40.7 15.7L41.7 15.8L42.5 16.3L43.1 17.0L43.5 17.8L43.7 18.8L43.6 19.9L43.3 20.9L42.9 21.7L42.4 22.4L41.9 22.9L41.4 23.0L40.9 24.8L40.4 25.5L39.8 26.2L39.0 26.8L38.1 27.3L37.0 27.6L35.7 27.8L34.2 27.8L32.4 27.6L30.4 27.2L28.2 26.7L25.8 26.1L23.2 25.3L20.5 24.4L17.8 23.5L14.9 22.5", IdCardGenerator.signaturePath(123456789L))

        val d = IdCardGenerator.deriveDetails(123456789L)
        assertEquals(IdCardGenerator.Guilloche(654, 324, 24, 5, 15, 0.23251150895841421, 80), d.guilloche)
        assertEquals(IdCardGenerator.Waves(2.102475087530911, 18.358931452035904, 3.256717231756287), d.waves)
        assertEquals(IdCardGenerator.HoloShape.HEX, d.holoShape)
        assertEquals(IdCardGenerator.HoloSpot.CORNER_TILT, d.holoSpot)
        assertEquals(1, d.stampStyle)
        assertEquals(-4, d.stampRot)
        assertEquals(-0.12488469295203686, d.stampDx, 0.0)
        assertEquals(-0.12565253656357528, d.stampDy, 0.0)
        assertEquals(0.6931104227202014, d.stampInk, 0.0)
        assertEquals(332, d.stampPress)
        assertEquals(false, d.secondStamp)
        assertEquals(4, d.secondRot)
        assertEquals(0, d.led)
        assertEquals(1, d.coat)
        assertEquals(3, d.chipCorner)
        assertEquals(IdCardGenerator.Smudge(72, 57, 1), d.smudge)
        assertEquals(null, d.bubble)
        assertEquals(IdCardGenerator.Misprint(0.23737087636254728, 0.15634780004620552), d.misprint)
        assertEquals(1256577108L, d.artSeed)

        val scratches = IdCardGenerator.scratches(123456789L, 3)
        assertEquals(IdCardGenerator.Scratch(92.14229972567409, 88.37099426891655, 83.770719030733, 90.6562832040776, 0.45839689605636524), scratches[0])
        assertEquals(IdCardGenerator.Scratch(1.6237961128354073, 18.81722833495587, 6.878700955819293, 22.996252162513073, 0.4979794139158912), scratches[1])
        assertEquals(IdCardGenerator.Scratch(55.75843614060432, 22.766995942220092, 56.81201718898899, 30.565389816654907, 0.19504135948373003), scratches[2])
    }

    @Test
    fun `seed 2147483647 совпадает с вебом`() {
        val traits = IdCardGenerator.generateTraits(2147483647L)
        assertEquals(2147483647L, traits.seed)
        assertEquals(IdFinish.BASE, traits.finish)
        assertEquals(true, traits.laminated)
        assertEquals(0, traits.wear)
        assertEquals(IdFoil.CLASSIC, traits.foil)
        assertEquals(1, traits.variant)
        assertEquals(1.4, traits.tilt, 0.0)
        assertEquals(IdEdition.COMMON, traits.edition)
        assertEquals("VL-KT2V-UJ8C", IdCardGenerator.generateSerial(2147483647L))
        assertEquals("A0BE20:157F9A:2B2BFA:07EDB8", IdCardGenerator.authKey(2147483647L))
        assertEquals("M4.8 23.0L3.7 22.6L3.1 21.4L3.0 19.6L3.4 17.2L4.4 14.5L5.9 11.7L7.9 9.0L10.1 6.6L12.5 4.8L14.9 3.6L17.1 3.2L19.1 3.6L20.6 4.8L21.6 6.6L22.0 9.0L21.9 11.7L21.2 14.5L20.2 17.2L18.8 19.6L17.3 21.4L15.8 22.6L14.4 23.0L14.4 23.0L14.2 22.9L14.0 22.6L14.1 22.1L14.3 21.5L14.7 20.8L15.4 20.1L16.2 19.4L17.2 18.8L18.2 18.3L19.3 18.0L20.4 17.9L21.4 18.0L22.3 18.3L23.0 18.8L23.5 19.4L23.8 20.1L23.9 20.8L23.8 21.5L23.5 22.1L23.2 22.6L22.9 22.9L22.5 23.0L22.5 23.0L22.3 22.9L22.3 22.5L22.4 21.9L22.7 21.1L23.2 20.2L23.9 19.3L24.7 18.4L25.6 17.6L26.6 17.0L27.5 16.6L28.4 16.5L29.2 16.6L29.9 17.0L30.4 17.6L30.7 18.4L30.8 19.3L30.8 20.2L30.6 21.1L30.3 21.9L30.0 22.5L29.7 22.9L29.4 23.0L29.4 23.0L29.1 22.9L29.0 22.5L29.0 22.0L29.3 21.3L29.7 20.5L30.5 19.6L31.3 18.8L32.4 18.1L33.5 17.5L34.7 17.2L35.8 17.1L36.9 17.2L37.8 17.5L38.5 18.1L39.0 18.8L39.3 19.6L39.3 20.5L39.2 21.3L38.9 22.0L38.6 22.5L38.2 22.9L37.8 23.0L37.8 23.0L37.3 22.9L36.9 22.6L36.8 22.0L36.9 21.4L37.2 20.6L37.9 19.8L38.7 19.0L39.8 18.3L41.0 17.8L42.2 17.5L43.5 17.4L44.6 17.5L45.6 17.8L46.4 18.3L47.0 19.0L47.2 19.8L47.2 20.6L47.0 21.4L46.6 22.0L46.1 22.6L45.5 22.9L44.9 23.0L44.9 23.0L44.4 22.9L44.2 22.5L44.1 21.9L44.3 21.1L44.7 20.2L45.4 19.3L46.2 18.4L47.2 17.6L48.3 17.0L49.5 16.6L50.6 16.4L51.6 16.6L52.5 17.0L53.1 17.6L53.5 18.4L53.7 19.3L53.7 20.2L53.4 21.1L53.0 21.9L52.4 22.5L51.9 22.9L51.4 23.0L51.4 23.0L51.1 22.7L51.1 21.9L51.4 20.7L51.9 19.1L52.7 17.2L53.7 15.3L54.9 13.5L56.2 11.9L57.4 10.6L58.6 9.8L59.6 9.5L60.5 9.8L61.0 10.6L61.3 11.9L61.4 13.5L61.1 15.3L60.7 17.2L60.1 19.1L59.4 20.7L58.8 21.9L58.1 22.7L57.7 23.0L56.4 24.8L55.1 25.5L53.8 26.2L52.3 26.8L50.6 27.3L48.7 27.6L46.7 27.8L44.4 27.8L41.8 27.6L39.1 27.2L36.1 26.7L32.9 26.1L29.6 25.3L26.2 24.4L22.6 23.5L19.0 22.5", IdCardGenerator.signaturePath(2147483647L))

        val d = IdCardGenerator.deriveDetails(2147483647L)
        assertEquals(IdCardGenerator.Guilloche(748, 348, 24, 5, 13, 0.15857656619511545, 92), d.guilloche)
        assertEquals(IdCardGenerator.Waves(2.5748415825888515, 19.438981615938246, 4.115572097525952), d.waves)
        assertEquals(IdCardGenerator.HoloShape.SHIELD, d.holoShape)
        assertEquals(IdCardGenerator.HoloSpot.CORNER_TILT, d.holoSpot)
        assertEquals(2, d.stampStyle)
        assertEquals(-21, d.stampRot)
        assertEquals(0.1406668736133725, d.stampDx, 0.0)
        assertEquals(0.05258692083880301, d.stampDy, 0.0)
        assertEquals(0.8200908852787688, d.stampInk, 0.0)
        assertEquals(38, d.stampPress)
        assertEquals(false, d.secondStamp)
        assertEquals(4, d.secondRot)
        assertEquals(7, d.led)
        assertEquals(2, d.coat)
        assertEquals(2, d.chipCorner)
        assertEquals(IdCardGenerator.Smudge(68, 23, 127), d.smudge)
        assertEquals(IdCardGenerator.Bubble(16, 54, 1.1992262985091657), d.bubble)
        assertEquals(IdCardGenerator.Misprint(0.0826254109852016, 0.19424035819247365), d.misprint)
        assertEquals(868535493L, d.artSeed)

        val scratches = IdCardGenerator.scratches(2147483647L, 3)
        assertEquals(IdCardGenerator.Scratch(56.711040483787656, 75.14286825899035, 65.01005711165796, 77.92692508374866, 0.3265940949204378), scratches[0])
        assertEquals(IdCardGenerator.Scratch(52.94492749962956, 85.72405215818435, 59.00169147045648, 88.52281359593948, 0.2758236404159106), scratches[1])
        assertEquals(IdCardGenerator.Scratch(49.047118006274104, 84.51674063690007, 42.26335640353012, 84.58316060420651, 0.36102698543109), scratches[2])
    }

    @Test
    fun `seed 2147483648 совпадает с вебом`() {
        val traits = IdCardGenerator.generateTraits(2147483648L)
        assertEquals(2147483648L, traits.seed)
        assertEquals(IdFinish.METALLIC, traits.finish)
        assertEquals(true, traits.laminated)
        assertEquals(2, traits.wear)
        assertEquals(IdFoil.CLASSIC, traits.foil)
        assertEquals(10, traits.variant)
        assertEquals(-0.2, traits.tilt, 0.0)
        assertEquals(IdEdition.UNCOMMON, traits.edition)
        assertEquals("VL-RBZY-YWHM", IdCardGenerator.generateSerial(2147483648L))
        assertEquals("239420:E0AB54:CBC401:8953EE", IdCardGenerator.authKey(2147483648L))
        assertEquals("M4.5 23.0L3.6 22.6L3.0 21.5L3.0 19.8L3.5 17.6L4.4 15.1L5.8 12.5L7.6 10.0L9.6 7.8L11.8 6.1L13.9 5.0L15.9 4.7L17.7 5.0L19.0 6.1L19.9 7.8L20.2 10.0L20.1 12.5L19.5 15.1L18.6 17.6L17.4 19.8L16.0 21.5L14.7 22.6L13.5 23.0L13.5 23.0L13.1 22.7L13.1 21.9L13.2 20.7L13.7 19.1L14.5 17.3L15.4 15.4L16.6 13.6L17.8 12.0L19.1 10.8L20.3 10.0L21.4 9.7L22.3 10.0L22.9 10.8L23.2 12.0L23.2 13.6L23.0 15.4L22.6 17.3L21.9 19.1L21.2 20.7L20.4 21.9L19.7 22.7L19.2 23.0L19.2 23.0L18.9 22.8L18.7 22.4L18.8 21.7L19.1 20.8L19.5 19.7L20.2 18.6L21.0 17.6L22.0 16.6L23.0 15.9L24.0 15.5L25.0 15.3L25.8 15.5L26.5 15.9L26.9 16.6L27.2 17.6L27.2 18.6L27.1 19.7L26.7 20.8L26.3 21.7L25.8 22.4L25.3 22.8L24.9 23.0L24.9 23.0L24.8 22.7L24.9 21.9L25.3 20.7L25.9 19.0L26.8 17.2L27.8 15.3L28.9 13.4L30.0 11.8L31.1 10.5L32.1 9.7L33.0 9.5L33.6 9.7L34.0 10.5L34.2 11.8L34.1 13.4L33.8 15.3L33.3 17.2L32.7 19.0L32.1 20.7L31.6 21.9L31.1 22.7L30.8 23.0L30.8 23.0L30.7 22.9L30.7 22.5L30.9 21.8L31.2 21.0L31.8 20.1L32.4 19.2L33.2 18.2L34.1 17.4L35.0 16.8L35.9 16.4L36.7 16.3L37.4 16.4L38.0 16.8L38.4 17.4L38.6 18.2L38.7 19.2L38.7 20.1L38.5 21.0L38.2 21.8L37.9 22.5L37.7 22.9L37.5 23.0L37.5 23.0L37.4 22.9L37.4 22.4L37.6 21.8L38.0 20.9L38.6 20.0L39.3 19.0L40.2 18.0L41.1 17.2L42.1 16.5L43.1 16.1L44.0 16.0L44.8 16.1L45.4 16.5L45.9 17.2L46.2 18.0L46.3 19.0L46.3 20.0L46.1 20.9L45.9 21.8L45.6 22.4L45.3 22.9L45.1 23.0L45.1 23.0L44.7 22.9L44.4 22.5L44.4 21.9L44.6 21.2L45.0 20.4L45.7 19.5L46.6 18.7L47.7 17.9L49.0 17.4L50.2 17.0L51.5 16.9L52.6 17.0L53.6 17.4L54.4 17.9L54.9 18.7L55.2 19.5L55.2 20.4L55.1 21.2L54.7 21.9L54.2 22.5L53.7 22.9L53.2 23.0L52.5 24.8L51.7 25.5L50.9 26.2L49.9 26.8L48.8 27.3L47.5 27.6L46.0 27.8L44.2 27.8L42.2 27.6L40.0 27.2L37.6 26.7L35.0 26.1L32.2 25.3L29.3 24.4L26.3 23.5L23.2 22.5", IdCardGenerator.signaturePath(2147483648L))

        val d = IdCardGenerator.deriveDetails(2147483648L)
        assertEquals(IdCardGenerator.Guilloche(710, 277, 16, 5, 13, 0.23180459246970714, 75), d.guilloche)
        assertEquals(IdCardGenerator.Waves(2.071723556984216, 19.490729502402246, 2.696811289083508), d.waves)
        assertEquals(IdCardGenerator.HoloShape.ROSETTE, d.holoShape)
        assertEquals(IdCardGenerator.HoloSpot.CORNER, d.holoSpot)
        assertEquals(1, d.stampStyle)
        assertEquals(-11, d.stampRot)
        assertEquals(-0.02321545626036825, d.stampDx, 0.0)
        assertEquals(0.029325623810291246, d.stampDy, 0.0)
        assertEquals(0.8125721760559828, d.stampInk, 0.0)
        assertEquals(9, d.stampPress)
        assertEquals(true, d.secondStamp)
        assertEquals(-6, d.secondRot)
        assertEquals(1, d.led)
        assertEquals(0, d.coat)
        assertEquals(1, d.chipCorner)
        assertEquals(IdCardGenerator.Smudge(36, 61, 116), d.smudge)
        assertEquals(IdCardGenerator.Bubble(14, 28, 0.9231309694703669), d.bubble)
        assertEquals(IdCardGenerator.Misprint(-0.09947207127697766, -0.12181867128238083), d.misprint)
        assertEquals(1808303083L, d.artSeed)

        val scratches = IdCardGenerator.scratches(2147483648L, 3)
        assertEquals(IdCardGenerator.Scratch(44.99280897434801, 13.600683910772204, 34.14774423823822, 16.846115784888045, 0.19269499336369333), scratches[0])
        assertEquals(IdCardGenerator.Scratch(73.78039723262191, 86.34665766730905, 77.3277548004239, 88.32841007901744, 0.1782391647924669), scratches[1])
        assertEquals(IdCardGenerator.Scratch(73.88942209072411, 79.51770890504122, 68.32651955473196, 81.29983205553349, 0.4350602704216726), scratches[2])
    }

    @Test
    fun `seed 4294967295 совпадает с вебом`() {
        val traits = IdCardGenerator.generateTraits(4294967295L)
        assertEquals(4294967295L, traits.seed)
        assertEquals(IdFinish.OBSIDIAN, traits.finish)
        assertEquals(true, traits.laminated)
        assertEquals(2, traits.wear)
        assertEquals(IdFoil.AURORA, traits.foil)
        assertEquals(10, traits.variant)
        assertEquals(0.1, traits.tilt, 0.0)
        assertEquals(IdEdition.EPIC, traits.edition)
        assertEquals("VL-VRZY-99M4", IdCardGenerator.generateSerial(4294967295L))
        assertEquals("E45120:B52F75:A17A59:ACA3A2", IdCardGenerator.authKey(4294967295L))
        assertEquals("M4.1 23.0L3.4 22.6L3.0 21.6L3.0 19.9L3.6 17.8L4.5 15.4L5.9 12.9L7.6 10.4L9.5 8.3L11.6 6.6L13.6 5.6L15.4 5.2L17.0 5.6L18.2 6.6L19.0 8.3L19.3 10.4L19.2 12.9L18.6 15.4L17.7 17.8L16.6 19.9L15.4 21.6L14.2 22.6L13.2 23.0L13.2 23.0L13.0 22.9L12.9 22.4L13.0 21.8L13.3 20.9L13.9 19.9L14.6 18.9L15.4 18.0L16.4 17.1L17.4 16.4L18.4 16.0L19.4 15.9L20.2 16.0L20.9 16.4L21.4 17.1L21.7 18.0L21.8 18.9L21.8 19.9L21.5 20.9L21.2 21.8L20.8 22.4L20.4 22.9L20.1 23.0L20.1 23.0L19.7 22.9L19.5 22.5L19.5 21.9L19.7 21.1L20.2 20.2L20.8 19.2L21.7 18.3L22.7 17.6L23.8 16.9L24.9 16.6L26.0 16.4L27.0 16.6L27.8 16.9L28.5 17.6L28.9 18.3L29.1 19.2L29.0 20.2L28.8 21.1L28.4 21.9L27.9 22.5L27.4 22.9L26.9 23.0L26.9 23.0L26.8 22.9L26.9 22.6L27.0 22.1L27.4 21.4L27.8 20.7L28.5 20.0L29.2 19.2L30.0 18.6L30.9 18.1L31.8 17.8L32.6 17.7L33.4 17.8L34.0 18.1L34.5 18.6L34.9 19.2L35.0 20.0L35.1 20.7L35.0 21.4L34.9 22.1L34.7 22.6L34.4 22.9L34.3 23.0L34.3 23.0L34.2 22.9L34.2 22.5L34.4 22.0L34.7 21.2L35.2 20.4L35.9 19.5L36.7 18.7L37.6 18.0L38.6 17.4L39.5 17.0L40.4 16.9L41.2 17.0L41.9 17.4L42.4 18.0L42.8 18.7L43.0 19.5L43.0 20.4L42.9 21.2L42.7 22.0L42.4 22.5L42.2 22.9L42.0 23.0L41.7 24.8L41.4 25.5L41.0 26.2L40.5 26.8L39.8 27.3L38.9 27.6L37.8 27.8L36.5 27.8L34.9 27.6L33.2 27.2L31.2 26.7L29.0 26.1L26.7 25.3L24.2 24.4L21.6 23.5L19.0 22.5", IdCardGenerator.signaturePath(4294967295L))

        val d = IdCardGenerator.deriveDetails(4294967295L)
        assertEquals(IdCardGenerator.Guilloche(736, 387, 16, 3, 12, 0.2321209790930152, 84), d.guilloche)
        assertEquals(IdCardGenerator.Waves(3.0020497776102273, 13.885670228861272, 2.508447033904775), d.waves)
        assertEquals(IdCardGenerator.HoloShape.ROSETTE, d.holoShape)
        assertEquals(IdCardGenerator.HoloSpot.CORNER_TILT, d.holoSpot)
        assertEquals(2, d.stampStyle)
        assertEquals(-4, d.stampRot)
        assertEquals(-0.31873134016059335, d.stampDx, 0.0)
        assertEquals(-0.5089478672482073, d.stampDy, 0.0)
        assertEquals(0.7722594098001718, d.stampInk, 0.0)
        assertEquals(216, d.stampPress)
        assertEquals(true, d.secondStamp)
        assertEquals(2, d.secondRot)
        assertEquals(5, d.led)
        assertEquals(3, d.coat)
        assertEquals(2, d.chipCorner)
        assertEquals(IdCardGenerator.Smudge(51, 58, 111), d.smudge)
        assertEquals(IdCardGenerator.Bubble(33, 52, 0.8884372243657708), d.bubble)
        assertEquals(IdCardGenerator.Misprint(0.15018713753670454, -0.04378853561356663), d.misprint)
        assertEquals(1013586046L, d.artSeed)

        val scratches = IdCardGenerator.scratches(4294967295L, 3)
        assertEquals(IdCardGenerator.Scratch(76.09093384817243, 43.15464370884001, 73.60665213538473, 46.28734521344311, 0.36598945514997466), scratches[0])
        assertEquals(IdCardGenerator.Scratch(87.40499012637883, 80.68585724104196, 84.23983504513949, 82.91141432745312, 0.4354567748727277), scratches[1])
        assertEquals(IdCardGenerator.Scratch(32.11352836806327, 2.207742794416845, 43.0482961740237, 7.210800219021982, 0.4137047777883708), scratches[2])
    }

    @Test
    fun `seed 3735928559 совпадает с вебом`() {
        val traits = IdCardGenerator.generateTraits(3735928559L)
        assertEquals(3735928559L, traits.seed)
        assertEquals(IdFinish.OBSIDIAN, traits.finish)
        assertEquals(true, traits.laminated)
        assertEquals(2, traits.wear)
        assertEquals(IdFoil.CLASSIC, traits.foil)
        assertEquals(5, traits.variant)
        assertEquals(1.0, traits.tilt, 0.0)
        assertEquals(IdEdition.RARE, traits.edition)
        assertEquals("VL-7FWD-ZDGE", IdCardGenerator.generateSerial(3735928559L))
        assertEquals("1AA02C:3DFBF4:034E45:546E14", IdCardGenerator.authKey(3735928559L))
        assertEquals("M3.9 23.0L3.3 22.6L3.0 21.5L3.2 19.6L3.9 17.3L5.0 14.6L6.6 11.9L8.5 9.2L10.6 6.9L12.8 5.0L14.9 3.9L16.9 3.5L18.5 3.9L19.8 5.0L20.6 6.9L20.9 9.2L20.8 11.9L20.3 14.6L19.4 17.3L18.3 19.6L17.1 21.5L15.9 22.6L15.0 23.0L15.0 23.0L14.7 22.8L14.6 22.4L14.6 21.7L14.9 20.8L15.4 19.8L16.1 18.8L17.0 17.7L18.0 16.9L19.0 16.2L20.1 15.7L21.1 15.6L22.0 15.7L22.7 16.2L23.2 16.9L23.5 17.7L23.6 18.8L23.5 19.8L23.3 20.8L22.9 21.7L22.4 22.4L21.9 22.8L21.5 23.0L21.5 23.0L21.2 22.8L21.1 22.1L21.2 21.0L21.6 19.7L22.3 18.1L23.2 16.5L24.2 14.9L25.4 13.6L26.6 12.5L27.7 11.8L28.7 11.6L29.6 11.8L30.2 12.5L30.6 13.6L30.7 14.9L30.6 16.5L30.2 18.1L29.7 19.7L29.1 21.0L28.4 22.1L27.8 22.8L27.3 23.0L27.3 23.0L27.3 22.7L27.5 21.9L28.1 20.5L28.8 18.8L29.8 16.8L30.9 14.8L32.2 12.8L33.5 11.1L34.7 9.7L35.8 8.9L36.8 8.6L37.5 8.9L38.0 9.7L38.2 11.1L38.2 12.8L37.9 14.8L37.5 16.8L37.0 18.8L36.5 20.5L35.9 21.9L35.5 22.7L35.3 23.0L35.3 23.0L35.0 22.8L34.9 22.4L35.0 21.7L35.3 20.8L35.8 19.7L36.4 18.7L37.3 17.6L38.2 16.7L39.2 16.0L40.2 15.6L41.1 15.4L41.9 15.6L42.5 16.0L43.0 16.7L43.2 17.6L43.3 18.7L43.1 19.7L42.8 20.8L42.4 21.7L41.9 22.4L41.5 22.8L41.1 23.0L41.1 23.0L40.5 22.9L40.1 22.5L39.9 21.8L40.0 21.1L40.4 20.1L41.0 19.2L41.9 18.3L43.0 17.5L44.2 16.9L45.4 16.5L46.6 16.3L47.8 16.5L48.7 16.9L49.5 17.5L49.9 18.3L50.1 19.2L50.0 20.1L49.7 21.1L49.2 21.8L48.5 22.5L47.8 22.9L47.1 23.0L46.7 24.8L46.3 25.5L45.7 26.2L45.0 26.8L44.2 27.3L43.2 27.6L41.9 27.8L40.5 27.8L38.8 27.6L36.8 27.2L34.7 26.7L32.4 26.1L29.9 25.3L27.3 24.4L24.5 23.5L21.8 22.5", IdCardGenerator.signaturePath(3735928559L))

        val d = IdCardGenerator.deriveDetails(3735928559L)
        assertEquals(IdCardGenerator.Guilloche(573, 388, 14, 7, 14, 0.17472937705926597, 74), d.guilloche)
        assertEquals(IdCardGenerator.Waves(1.799203580291942, 19.462942951358855, 4.907183594385107), d.waves)
        assertEquals(IdCardGenerator.HoloShape.ROSETTE, d.holoShape)
        assertEquals(IdCardGenerator.HoloSpot.CORNER, d.holoSpot)
        assertEquals(0, d.stampStyle)
        assertEquals(-9, d.stampRot)
        assertEquals(0.10341488700360058, d.stampDx, 0.0)
        assertEquals(0.189408412668854, d.stampDy, 0.0)
        assertEquals(0.8076548049831763, d.stampInk, 0.0)
        assertEquals(351, d.stampPress)
        assertEquals(false, d.secondStamp)
        assertEquals(-2, d.secondRot)
        assertEquals(6, d.led)
        assertEquals(2, d.coat)
        assertEquals(0, d.chipCorner)
        assertEquals(IdCardGenerator.Smudge(72, 49, 169), d.smudge)
        assertEquals(null, d.bubble)
        assertEquals(IdCardGenerator.Misprint(0.0057247193763032556, -0.08646559361368418), d.misprint)
        assertEquals(569453895L, d.artSeed)

        val scratches = IdCardGenerator.scratches(3735928559L, 3)
        assertEquals(IdCardGenerator.Scratch(9.492200869135559, 82.30413990095258, 12.705311003990222, 86.92263887357262, 0.17425403558881952), scratches[0])
        assertEquals(IdCardGenerator.Scratch(1.811532536521554, 69.88531136885285, 4.297385582555217, 71.54950103877378, 0.3368956206832081), scratches[1])
        assertEquals(IdCardGenerator.Scratch(44.743269914761186, 35.482536209747195, 51.759086708551386, 36.260426870633694, 0.45734194255201144), scratches[2])
    }

    @Test
    fun `seed 987654321 совпадает с вебом`() {
        val traits = IdCardGenerator.generateTraits(987654321L)
        assertEquals(987654321L, traits.seed)
        assertEquals(IdFinish.OBSIDIAN, traits.finish)
        assertEquals(true, traits.laminated)
        assertEquals(0, traits.wear)
        assertEquals(IdFoil.AURORA, traits.foil)
        assertEquals(7, traits.variant)
        assertEquals(-0.4, traits.tilt, 0.0)
        assertEquals(IdEdition.EPIC, traits.edition)
        assertEquals("VL-F3HV-C2S7", IdCardGenerator.generateSerial(987654321L))
        assertEquals("82BEEC:5FF083:5DC6AD:265959", IdCardGenerator.authKey(987654321L))
        assertEquals("M4.4 23.0L3.5 22.6L3.0 21.6L3.0 19.9L3.5 17.8L4.4 15.3L5.9 12.8L7.6 10.3L9.7 8.2L11.9 6.5L14.0 5.4L16.0 5.1L17.8 5.4L19.1 6.5L20.1 8.2L20.5 10.3L20.4 12.8L19.9 15.3L19.0 17.8L17.8 19.9L16.5 21.6L15.2 22.6L14.1 23.0L14.1 23.0L13.7 22.9L13.5 22.5L13.5 22.0L13.7 21.3L14.1 20.5L14.7 19.7L15.5 18.9L16.5 18.2L17.5 17.7L18.6 17.4L19.7 17.3L20.7 17.4L21.5 17.7L22.2 18.2L22.6 18.9L22.8 19.7L22.8 20.5L22.7 21.3L22.3 22.0L21.9 22.5L21.4 22.9L20.9 23.0L20.9 23.0L20.9 22.7L21.1 21.8L21.6 20.5L22.4 18.8L23.4 16.8L24.5 14.7L25.8 12.7L27.1 11.0L28.3 9.7L29.5 8.8L30.4 8.5L31.2 8.8L31.6 9.7L31.9 11.0L31.8 12.7L31.6 14.7L31.2 16.8L30.6 18.8L30.1 20.5L29.5 21.8L29.1 22.7L28.9 23.0L28.9 23.0L28.8 22.9L28.8 22.6L28.9 22.1L29.2 21.4L29.7 20.7L30.3 20.0L31.0 19.2L31.8 18.6L32.7 18.1L33.6 17.8L34.4 17.7L35.1 17.8L35.7 18.1L36.2 18.6L36.5 19.2L36.7 20.0L36.7 20.7L36.7 21.4L36.5 22.1L36.2 22.6L36.0 22.9L35.8 23.0L35.8 23.0L35.4 22.9L35.1 22.6L34.9 22.1L35.0 21.4L35.3 20.7L35.9 19.9L36.7 19.2L37.6 18.5L38.6 18.0L39.7 17.7L40.8 17.6L41.8 17.7L42.6 18.0L43.3 18.5L43.7 19.2L43.9 19.9L43.9 20.7L43.6 21.4L43.2 22.1L42.7 22.6L42.2 22.9L41.6 23.0L41.6 23.0L41.4 22.7L41.6 21.8L42.0 20.3L42.7 18.5L43.7 16.3L44.9 14.1L46.3 12.0L47.7 10.1L49.1 8.7L50.5 7.8L51.6 7.5L52.5 7.8L53.2 8.7L53.5 10.1L53.5 12.0L53.3 14.1L52.8 16.3L52.2 18.5L51.5 20.3L50.8 21.8L50.2 22.7L49.8 23.0L48.9 24.8L47.9 25.5L46.9 26.2L45.7 26.8L44.4 27.3L42.9 27.6L41.1 27.8L39.2 27.8L37.0 27.6L34.6 27.2L31.9 26.7L29.1 26.1L26.1 25.3L23.0 24.4L19.8 23.5L16.5 22.5", IdCardGenerator.signaturePath(987654321L))

        val d = IdCardGenerator.deriveDetails(987654321L)
        assertEquals(IdCardGenerator.Guilloche(603, 366, 14, 5, 13, 0.18389751280657946, 80), d.guilloche)
        assertEquals(IdCardGenerator.Waves(1.6873355699237438, 18.689669295214117, 4.228759794099267), d.waves)
        assertEquals(IdCardGenerator.HoloShape.ROSETTE, d.holoShape)
        assertEquals(IdCardGenerator.HoloSpot.CORNER, d.holoSpot)
        assertEquals(1, d.stampStyle)
        assertEquals(-8, d.stampRot)
        assertEquals(0.2494343970902264, d.stampDx, 0.0)
        assertEquals(-0.20372081408277154, d.stampDy, 0.0)
        assertEquals(0.8291781894117594, d.stampInk, 0.0)
        assertEquals(172, d.stampPress)
        assertEquals(true, d.secondStamp)
        assertEquals(3, d.secondRot)
        assertEquals(1, d.led)
        assertEquals(3, d.coat)
        assertEquals(1, d.chipCorner)
        assertEquals(IdCardGenerator.Smudge(30, 68, 31), d.smudge)
        assertEquals(IdCardGenerator.Bubble(76, 23, 0.9149379394948483), d.bubble)
        assertEquals(IdCardGenerator.Misprint(0.1675560858566314, -0.14408518141135573), d.misprint)
        assertEquals(1914730159L, d.artSeed)

        val scratches = IdCardGenerator.scratches(987654321L, 3)
        assertEquals(IdCardGenerator.Scratch(78.9533672388643, 87.26762847509235, 77.51636241440615, 89.54061747541671, 0.49926682492950925), scratches[0])
        assertEquals(IdCardGenerator.Scratch(11.682467861101031, 7.708214106969535, 8.33090344795127, 8.206829560619159, 0.18389920595800502), scratches[1])
        assertEquals(IdCardGenerator.Scratch(20.57069733273238, 20.909897261299193, 26.787743517072222, 21.503902641986052, 0.4008621010696515), scratches[2])
    }

    @Test
    fun `MRZ совпадает с вебом, включая транслитерацию`() {
        assertEquals(listOf("IDPVLKVL<7KQM<3XPA<<<<<<<<<<<<", "250314<261005<VLK<IVAN<PETROV<", "IVAN<PETR<SHCHUKIN<<<<<<<<<<<<"),
            IdCardGenerator.mrzLines("VL-7KQM-3XPA", IdMode.PROTOGEN, "ivan_petrov", "Иван Пётр-Щукин", 1741910400000L, 1791200000000L))
        assertEquals(listOf("IDSVLKVL<ABCD<2345<<<<<<<<<<<<", "700101<710205<VLK<ZOE<<<<<<<<<", "ZOE<<<<<<<<<<<<<<<<<<<<<<<<<<<"),
            IdCardGenerator.mrzLines("VL-ABCD-2345", IdMode.STANDARD, "zoë", "", 0L, 86400000L * 400))
        assertEquals(listOf("IDBVLKVL<ABCD<2345<<<<<<<<<<<<", "231114<231114<VLK<EZHIK<<<<<<<", "GANNA<IZHAK<<<<<<<<<<<<<<<<<<<"),
            IdCardGenerator.mrzLines("VL-ABCD-2345", IdMode.BEAST, "Ёжик", "Ґанна Їжак 🐾", 1700000000000L, 1700000000000L))
    }

    @Test
    fun `штрихкод совпадает с вебом`() {
        assertEquals(listOf(1 to true, 2 to false, 2 to true, 1 to false, 2 to true, 1 to false, 1 to true, 1 to false, 2 to true, 2 to false, 1 to true, 1 to false, 2 to true, 1 to false, 2 to true, 2 to false, 1 to true, 1 to false, 2 to true, 2 to false, 2 to true, 1 to false, 2 to true, 1 to false, 2 to true, 2 to false, 1 to true, 2 to false, 1 to true, 1 to false), IdCardGenerator.barcodeBars("VL-7K"))
    }

    @Test
    fun `toFixed как в JS`() {
        assertEquals("1.3", IdCardGenerator.toFixed1(1.25))
        assertEquals("1.4", IdCardGenerator.toFixed1(1.35))
        assertEquals("-0.0", IdCardGenerator.toFixed1(-0.04))
        assertEquals("23.0", IdCardGenerator.toFixed1(23.0))
    }
}
