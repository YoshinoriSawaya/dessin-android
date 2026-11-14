package com.yswy.dessin.gl

/**
 * 光の当たる不透明な面。Web版(three.js r128 の MeshStandardMaterial、半球光 0.75 と平行光 0.85)の
 * 拡散反射だけを近似する(鏡面反射は省く)。軸ごとの拡大があるので法線行列はシェーダで求める。
 * 体に落ちる影(E11): 太陽から見た奥行き(uShadowMap)と比べ、影の中では平行光の分を消す。uShadow が 0 なら影なし
 */
object LitShader {
    const val VERTEX = """#version 300 es
uniform mat4 uViewProjection;
uniform mat4 uModel;
uniform mat4 uLightViewProjection;
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
out vec3 vNormal;
out vec4 vLightPos;
void main() {
    mat3 normalMatrix = transpose(inverse(mat3(uModel)));
    vNormal = normalize(normalMatrix * aNormal);
    vec4 world = uModel * vec4(aPosition, 1.0);
    vLightPos = uLightViewProjection * world;
    gl_Position = uViewProjection * world;
}
"""

    const val FRAGMENT = """#version 300 es
precision mediump float;
uniform vec3 uColor;
uniform vec3 uEmissive;
uniform float uOpacity;
uniform float uShadow;
uniform highp sampler2DShadow uShadowMap;
in vec3 vNormal;
in vec4 vLightPos;
out vec4 fragColor;
const vec3 SKY = vec3(1.0);
const vec3 GROUND = vec3(0.616, 0.596, 0.549);
// 平行光はWeb版の sun.position (2.2, 4.5, 3.2) の方向から
const vec3 SUN_DIR = vec3(2.2, 4.5, 3.2) / 5.943904440685432;
// 太陽から見た奥行きと比べて、光が届く割合(0〜1)。3×3 の点を平均してふちをぼかす
float sunVisibility(vec3 n) {
    if (uShadow < 0.5) return 1.0;
    vec3 p = vLightPos.xyz / vLightPos.w * 0.5 + 0.5;
    if (p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0 || p.z > 1.0) return 1.0;
    // 面が光に対して斜めなほど、自分の影(縞)を避けるためのずらしを大きくする
    float bias = 0.0015 + 0.004 * (1.0 - max(dot(n, SUN_DIR), 0.0));
    vec2 texel = 1.0 / vec2(textureSize(uShadowMap, 0));
    float sum = 0.0;
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            sum += texture(uShadowMap, vec3(p.xy + vec2(x, y) * texel, p.z - bias));
        }
    }
    return sum / 9.0;
}
void main() {
    vec3 n = normalize(gl_FrontFacing ? vNormal : -vNormal);
    vec3 hemi = mix(GROUND, SKY, 0.5 * n.y + 0.5) * 0.75;
    float lambert = max(dot(n, SUN_DIR), 0.0);
    vec3 sun = vec3(lambert * 0.85 * (lambert > 0.0 ? sunVisibility(n) : 1.0));
    fragColor = vec4(uColor * (hemi + sun) + uEmissive, uOpacity);
}
"""
}

/** 光の当たらない単色(線・格子・可動域・玉) */
object FlatShader {
    const val VERTEX = """#version 300 es
uniform mat4 uViewProjection;
uniform mat4 uModel;
layout(location = 0) in vec3 aPosition;
void main() {
    gl_Position = uViewProjection * uModel * vec4(aPosition, 1.0);
}
"""

    const val FRAGMENT = """#version 300 es
precision mediump float;
uniform vec4 uColor;
out vec4 fragColor;
void main() {
    fragColor = uColor;
}
"""
}

/** 影の下描き(E11)。太陽から見た奥行きだけを書く */
object DepthShader {
    const val VERTEX = """#version 300 es
uniform mat4 uLightViewProjection;
uniform mat4 uModel;
layout(location = 0) in vec3 aPosition;
void main() {
    gl_Position = uLightViewProjection * uModel * vec4(aPosition, 1.0);
}
"""

    const val FRAGMENT = """#version 300 es
precision mediump float;
void main() {
}
"""
}
