package top.liquidglass.vector;
/** Exact rounded-rectangle refraction AGSL from Kyant0 AndroidLiquidGlass kmp, Shaders.kt.
 * Apache-2.0; copyright 2025 Kyant. See kyant-backdrop-engine/LICENSE.
 * Kept verbatim, apart from Kotlin interpolation of RoundedRectSDF.
 */
final class KyantOriginalShader251 {
    static final String SOURCE =
        "\n" +
        "uniform shader content;\n" +
        "\n" +
        "uniform float2 size;\n" +
        "uniform float2 offset;\n" +
        "uniform float4 cornerRadii;\n" +
        "uniform float refractionHeight;\n" +
        "uniform float refractionAmount;\n" +
        "uniform float depthEffect;\n" +
        "\n" +
        "\n" +
        "float radiusAt(float2 coord, float4 radii) {\n" +
        "    if (coord.x >= 0.0) {\n" +
        "        if (coord.y <= 0.0) return radii.y;\n" +
        "        else return radii.z;\n" +
        "    } else {\n" +
        "        if (coord.y <= 0.0) return radii.x;\n" +
        "        else return radii.w;\n" +
        "    }\n" +
        "}\n" +
        "\n" +
        "float sdRoundedRect(float2 coord, float2 halfSize, float radius) {\n" +
        "    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n" +
        "    float outside = length(max(cornerCoord, 0.0)) - radius;\n" +
        "    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);\n" +
        "    return outside + inside;\n" +
        "}\n" +
        "\n" +
        "float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {\n" +
        "    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n" +
        "    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {\n" +
        "        return sign(coord) * normalize(max(cornerCoord, 0.0));\n" +
        "    } else {\n" +
        "        float gradX = step(cornerCoord.y, cornerCoord.x);\n" +
        "        return sign(coord) * float2(gradX, 1.0 - gradX);\n" +
        "    }\n" +
        "}\n" +
        "\n" +
        "float circleMap(float x) {\n" +
        "    return 1.0 - sqrt(1.0 - x * x);\n" +
        "}\n" +
        "\n" +
        "half4 main(float2 coord) {\n" +
        "    float2 halfSize = size * 0.5;\n" +
        "    float2 centeredCoord = (coord + offset) - halfSize;\n" +
        "    float radius = radiusAt(coord, cornerRadii);\n" +
        "    \n" +
        "    float sd = sdRoundedRect(centeredCoord, halfSize, radius);\n" +
        "    if (-sd >= refractionHeight) {\n" +
        "        return content.eval(coord);\n" +
        "    }\n" +
        "    sd = min(sd, 0.0);\n" +
        "    \n" +
        "    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;\n" +
        "    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));\n" +
        "    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));\n" +
        "    \n" +
        "    float2 refractedCoord = coord + d * grad;\n" +
        "    return content.eval(refractedCoord);\n" +
        "}\n";
}
