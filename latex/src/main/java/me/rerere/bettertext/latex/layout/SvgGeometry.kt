package me.rerere.bettertext.latex.layout

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor

/*
 * Parametric SVG path geometry ported from KaTeX's svgGeometry.ts. The static
 * paths of that file are available as [SvgPaths]. In all paths, the
 * viewBox-to-em scale is 1000:1.
 */

/** Formats a number for a path string without scientific notation. */
private fun n(x: Double): String {
    if (x == floor(x) && abs(x) < 1e15) return x.toLong().toString()
    return BigDecimal(x).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

// Second Brush Stroke
//    Low resolution monitors struggle to display images in fine detail.
//    So browsers apply anti-aliasing. A long straight arrow shaft therefore
//    will sometimes appear as if it has a blurred edge.
//
//    To mitigate this, these SVG files contain a second "brush-stroke" on the
//    arrow shafts. That is, a second long thin rectangular SVG path has been
//    written directly on top of each arrow shaft.
private fun doubleBrushStroke(svgPath: String): String = "$svgPath $svgPath"

/** Padding above a sqrt vinculum. Prevents image cropping. */
private const val H_LINE_PAD = 80.0

// The vinculum of a \sqrt can be made thicker by a KaTeX rendering option.
// Think of variable extraVinculum as two detours in the SVG path.
// The detour begins at the lower left of the area labeled extraVinculum below.
// The detour proceeds one extraVinculum distance up and slightly to the right,
// displacing the radiused corner between surd and vinculum. The radius is
// traversed as usual, then the detour resumes. It goes right, to the end of
// the very long vinculum, then down one extraVinculum distance,
// after which it resumes regular path geometry for the radical.

private fun sqrtMain(extraVinculum: Double, hLinePad: Double): String {
    // sqrtMain path geometry is from glyph U221A in the font KaTeX Main
    return """M95,${n(622 + extraVinculum + hLinePad)}
c-2.7,0,-7.17,-2.7,-13.5,-8c-5.8,-5.3,-9.5,-10,-9.5,-14
c0,-2,0.3,-3.3,1,-4c1.3,-2.7,23.83,-20.7,67.5,-54
c44.2,-33.3,65.8,-50.3,66.5,-51c1.3,-1.3,3,-2,5,-2c4.7,0,8.7,3.3,12,10
s173,378,173,378c0.7,0,35.3,-71,104,-213c68.7,-142,137.5,-285,206.5,-429
c69,-144,104.5,-217.7,106.5,-221
l${n(extraVinculum / 2.075)} -${n(extraVinculum)}
c5.3,-9.3,12,-14,20,-14
H400000v${n(40 + extraVinculum)}H845.2724
s-225.272,467,-225.272,467s-235,486,-235,486c-2.7,4.7,-9,7,-19,7
c-6,0,-10,-1,-12,-3s-194,-422,-194,-422s-65,47,-65,47z
M${n(834 + extraVinculum)} ${n(hLinePad)}h400000v${n(40 + extraVinculum)}h-400000z"""
}

private fun sqrtSize1(extraVinculum: Double, hLinePad: Double): String {
    // size1 is from glyph U221A in the font KaTeX_Size1-Regular
    return """M263,${n(601 + extraVinculum + hLinePad)}c0.7,0,18,39.7,52,119
c34,79.3,68.167,158.7,102.5,238c34.3,79.3,51.8,119.3,52.5,120
c340,-704.7,510.7,-1060.3,512,-1067
l${n(extraVinculum / 2.084)} -${n(extraVinculum)}
c4.7,-7.3,11,-11,19,-11
H40000v${n(40 + extraVinculum)}H1012.3
s-271.3,567,-271.3,567c-38.7,80.7,-84,175,-136,283c-52,108,-89.167,185.3,-111.5,232
c-22.3,46.7,-33.8,70.3,-34.5,71c-4.7,4.7,-12.3,7,-23,7s-12,-1,-12,-1
s-109,-253,-109,-253c-72.7,-168,-109.3,-252,-110,-252c-10.7,8,-22,16.7,-34,26
c-22,17.3,-33.3,26,-34,26s-26,-26,-26,-26s76,-59,76,-59s76,-60,76,-60z
M${n(1001 + extraVinculum)} ${n(hLinePad)}h400000v${n(40 + extraVinculum)}h-400000z"""
}

private fun sqrtSize2(extraVinculum: Double, hLinePad: Double): String {
    // size2 is from glyph U221A in the font KaTeX_Size2-Regular
    return """M983 ${n(10 + extraVinculum + hLinePad)}
l${n(extraVinculum / 3.13)} -${n(extraVinculum)}
c4,-6.7,10,-10,18,-10 H400000v${n(40 + extraVinculum)}
H1013.1s-83.4,268,-264.1,840c-180.7,572,-277,876.3,-289,913c-4.7,4.7,-12.7,7,-24,7
s-12,0,-12,0c-1.3,-3.3,-3.7,-11.7,-7,-25c-35.3,-125.3,-106.7,-373.3,-214,-744
c-10,12,-21,25,-33,39s-32,39,-32,39c-6,-5.3,-15,-14,-27,-26s25,-30,25,-30
c26.7,-32.7,52,-63,76,-91s52,-60,52,-60s208,722,208,722
c56,-175.3,126.3,-397.3,211,-666c84.7,-268.7,153.8,-488.2,207.5,-658.5
c53.7,-170.3,84.5,-266.8,92.5,-289.5z
M${n(1001 + extraVinculum)} ${n(hLinePad)}h400000v${n(40 + extraVinculum)}h-400000z"""
}

private fun sqrtSize3(extraVinculum: Double, hLinePad: Double): String {
    // size3 is from glyph U221A in the font KaTeX_Size3-Regular
    return """M424,${n(2398 + extraVinculum + hLinePad)}
c-1.3,-0.7,-38.5,-172,-111.5,-514c-73,-342,-109.8,-513.3,-110.5,-514
c0,-2,-10.7,14.3,-32,49c-4.7,7.3,-9.8,15.7,-15.5,25c-5.7,9.3,-9.8,16,-12.5,20
s-5,7,-5,7c-4,-3.3,-8.3,-7.7,-13,-13s-13,-13,-13,-13s76,-122,76,-122s77,-121,77,-121
s209,968,209,968c0,-2,84.7,-361.7,254,-1079c169.3,-717.3,254.7,-1077.7,256,-1081
l${n(extraVinculum / 4.223)} -${n(extraVinculum)}c4,-6.7,10,-10,18,-10 H400000
v${n(40 + extraVinculum)}H1014.6
s-87.3,378.7,-272.6,1166c-185.3,787.3,-279.3,1182.3,-282,1185
c-2,6,-10,9,-24,9
c-8,0,-12,-0.7,-12,-2z M${n(1001 + extraVinculum)} ${n(hLinePad)}
h400000v${n(40 + extraVinculum)}h-400000z"""
}

private fun sqrtSize4(extraVinculum: Double, hLinePad: Double): String {
    // size4 is from glyph U221A in the font KaTeX_Size4-Regular
    return """M473,${n(2713 + extraVinculum + hLinePad)}
c339.3,-1799.3,509.3,-2700,510,-2702 l${n(extraVinculum / 5.298)} -${n(extraVinculum)}
c3.3,-7.3,9.3,-11,18,-11 H400000v${n(40 + extraVinculum)}H1017.7
s-90.5,478,-276.2,1466c-185.7,988,-279.5,1483,-281.5,1485c-2,6,-10,9,-24,9
c-8,0,-12,-0.7,-12,-2c0,-1.3,-5.3,-32,-16,-92c-50.7,-293.3,-119.7,-693.3,-207,-1200
c0,-1.3,-5.3,8.7,-16,30c-10.7,21.3,-21.3,42.7,-32,64s-16,33,-16,33s-26,-26,-26,-26
s76,-153,76,-153s77,-151,77,-151c0.7,0.7,35.7,202,105,604c67.3,400.7,102,602.7,104,
606zM${n(1001 + extraVinculum)} ${n(hLinePad)}h400000v${n(40 + extraVinculum)}H1017.7z"""
}

private fun sqrtTall(extraVinculum: Double, hLinePad: Double, viewBoxHeight: Double): String {
    // sqrtTall is from glyph U23B7 in the font KaTeX_Size4-Regular
    // One path edge has a variable length. It runs vertically from the vinculum
    // to a point near (14 units) the bottom of the surd. The vinculum
    // is normally 40 units thick. So the length of the line in question is:
    val vertSegment = viewBoxHeight - 54 - hLinePad - extraVinculum

    // Note: "H400000${40 + extraVinculum}" concatenates the numbers, as in KaTeX.
    return """M702 ${n(extraVinculum + hLinePad)}H400000${n(40 + extraVinculum)}
H742v${n(vertSegment)}l-4 4-4 4c-.667.7 -2 1.5-4 2.5s-4.167 1.833-6.5 2.5-5.5 1-9.5 1
h-12l-28-84c-16.667-52-96.667 -294.333-240-727l-212 -643 -85 170
c-4-3.333-8.333-7.667-13 -13l-13-13l77-155 77-156c66 199.333 139 419.667
219 661 l218 661zM702 ${n(hLinePad)}H400000v${n(40 + extraVinculum)}H742z"""
}

/**
 * The path of a surd named [size] (`sqrtMain`, `sqrtSize1..4`, `sqrtTall`).
 * [extraVinculum] is in document em; [viewBoxHeight] in viewBox units.
 */
internal fun sqrtPath(size: String, extraVinculum: Float, viewBoxHeight: Float): String {
    val extra = 1000.0 * extraVinculum // Convert from document ems to viewBox.
    return when (size) {
        "sqrtMain" -> sqrtMain(extra, H_LINE_PAD)
        "sqrtSize1" -> sqrtSize1(extra, H_LINE_PAD)
        "sqrtSize2" -> sqrtSize2(extra, H_LINE_PAD)
        "sqrtSize3" -> sqrtSize3(extra, H_LINE_PAD)
        "sqrtSize4" -> sqrtSize4(extra, H_LINE_PAD)
        "sqrtTall" -> sqrtTall(extra, H_LINE_PAD, viewBoxHeight.toDouble())
        else -> ""
    }
}

/** The inner (repeated) part of stretchy tall delimiters; [height] is in viewBox units. */
internal fun innerPath(name: String, height: Int): String = when (name) {
    "⎜" -> doubleBrushStroke("M291 0 H417 V$height H291z")
    "∣" -> doubleBrushStroke("M145 0 H188 V$height H145z")
    "∥" -> doubleBrushStroke("M145 0 H188 V$height H145z") +
        doubleBrushStroke("M367 0 H410 V$height H367z")
    "⎟" -> doubleBrushStroke("M457 0 H583 V$height H457z")
    "⎢" -> doubleBrushStroke("M319 0 H403 V$height H319z")
    "⎥" -> doubleBrushStroke("M263 0 H347 V$height H263z")
    "⎪" -> doubleBrushStroke("M384 0 H504 V$height H384z")
    "⏐" -> doubleBrushStroke("M312 0 H355 V$height H312z")
    "‖" -> doubleBrushStroke("M257 0 H300 V$height H257z") +
        doubleBrushStroke("M478 0 H521 V$height H478z")
    else -> ""
}

/** A tall delimiter drawn as a single SVG; [midHeight] is the stretchable length in viewBox units. */
internal fun tallDelim(label: String, midHeight: Int): String = when (label) {
    "lbrack" -> """M403 1759 V84 H666 V0 H319 V1759 v$midHeight v1759 v84 h347 v-84
H403z M403 1759 V0 H319 V1759 v$midHeight v1759 v84 h84z"""
    "rbrack" -> """M347 1759 V0 H0 V84 H263 V1759 v$midHeight v1759 H0 v84 H347z
M347 1759 V0 H263 V1759 v$midHeight v1759 h84z"""
    "vert" -> """M145 15 v585 v$midHeight v585 c2.667,10,9.667,15,21,15
c10,0,16.667,-5,20,-15 v-585 v${-midHeight} v-585 c-2.667,-10,-9.667,-15,-21,-15
c-10,0,-16.667,5,-20,15z M188 15 H145 v585 v$midHeight v585 h43z"""
    "doublevert" -> """M145 15 v585 v$midHeight v585 c2.667,10,9.667,15,21,15
c10,0,16.667,-5,20,-15 v-585 v${-midHeight} v-585 c-2.667,-10,-9.667,-15,-21,-15
c-10,0,-16.667,5,-20,15z M188 15 H145 v585 v$midHeight v585 h43z
M367 15 v585 v$midHeight v585 c2.667,10,9.667,15,21,15
c10,0,16.667,-5,20,-15 v-585 v${-midHeight} v-585 c-2.667,-10,-9.667,-15,-21,-15
c-10,0,-16.667,5,-20,15z M410 15 H367 v585 v$midHeight v585 h43z"""
    // KaTeX writes "MM319" for the second brush stroke here; a single M is
    // used so the path parser accepts it.
    "lfloor" -> """M319 602 V0 H403 V602 v$midHeight v1715 h263 v84 H319z
M319 602 V0 H403 V602 v$midHeight v1715 H319z"""
    "rfloor" -> """M319 602 V0 H403 V602 v$midHeight v1799 H0 v-84 H319z
M319 602 V0 H403 V602 v$midHeight v1715 H319z"""
    "lceil" -> """M403 1759 V84 H666 V0 H319 V1759 v$midHeight v602 h84z
M403 1759 V0 H319 V1759 v$midHeight v602 h84z"""
    "rceil" -> """M347 1759 V0 H0 V84 H263 V1759 v$midHeight v602 h84z
M347 1759 V0 h-84 V1759 v$midHeight v602 h84z"""
    "lparen" -> """M863,9c0,-2,-2,-5,-6,-9c0,0,-17,0,-17,0c-12.7,0,-19.3,0.3,-20,1
c-5.3,5.3,-10.3,11,-15,17c-242.7,294.7,-395.3,682,-458,1162c-21.3,163.3,-33.3,349,
-36,557 l0,${midHeight + 84}c0.2,6,0,26,0,60c2,159.3,10,310.7,24,454c53.3,528,210,
949.7,470,1265c4.7,6,9.7,11.7,15,17c0.7,0.7,7,1,19,1c0,0,18,0,18,0c4,-4,6,-7,6,-9
c0,-2.7,-3.3,-8.7,-10,-18c-135.3,-192.7,-235.5,-414.3,-300.5,-665c-65,-250.7,-102.5,
-544.7,-112.5,-882c-2,-104,-3,-167,-3,-189
l0,-${midHeight + 92}c0,-162.7,5.7,-314,17,-454c20.7,-272,63.7,-513,129,-723c65.3,
-210,155.3,-396.3,270,-559c6.7,-9.3,10,-15.3,10,-18z"""
    "rparen" -> """M76,0c-16.7,0,-25,3,-25,9c0,2,2,6.3,6,13c21.3,28.7,42.3,60.3,
63,95c96.7,156.7,172.8,332.5,228.5,527.5c55.7,195,92.8,416.5,111.5,664.5
c11.3,139.3,17,290.7,17,454c0,28,1.7,43,3.3,45l0,${midHeight + 9}
c-3,4,-3.3,16.7,-3.3,38c0,162,-5.7,313.7,-17,455c-18.7,248,-55.8,469.3,-111.5,664
c-55.7,194.7,-131.8,370.3,-228.5,527c-20.7,34.7,-41.7,66.3,-63,95c-2,3.3,-4,7,-6,11
c0,7.3,5.7,11,17,11c0,0,11,0,11,0c9.3,0,14.3,-0.3,15,-1c5.3,-5.3,10.3,-11,15,-17
c242.7,-294.7,395.3,-681.7,458,-1161c21.3,-164.7,33.3,-350.7,36,-558
l0,-${midHeight + 144}c-2,-159.3,-10,-310.7,-24,-454c-53.3,-528,-210,-949.7,
-470,-1265c-4.7,-6,-9.7,-11.7,-15,-17c-0.7,-0.7,-6.7,-1,-18,-1z"""
    // We should not ever get here.
    else -> error("Unknown stretchy delimiter.")
}
