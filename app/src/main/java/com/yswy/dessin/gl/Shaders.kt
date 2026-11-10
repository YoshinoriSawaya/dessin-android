package com.yswy.dessin.gl

/**
 * 光の当たる不透明な面。Web版(three.js r128 の MeshStandardMaterial、半球光 0.75 と平行光 0.85)の
 * 拡散反射だけを近似する(鏡面反射と影は省く)。軸ごとの拡大があるので法線行列はシェーダで求める。
 */
object LitShader {
    const val VERTEX = """#version 300 es
uniform mat4 uViewProjection;
uniform mat4 uModel;
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
out vec3 vNormal;
void main() {
    mat3 normalMatrix = transpose(inverse(mat3(uModel)));
    vNormal = normalize(normalMatrix * aNormal);
    gl_Position = uViewProjection * uModel * vec4(aPosition, 1.0);
}
"""

    const val FRAGMENT = """#version 300 es
precision mediump float;
uniform vec3 uColor;
uniform vec3 uEmissive;
uniform float uOpacity;
in vec3 vNormal;
out vec4 fragColor;
const vec3 SKY = vec3(1.0);
const vec3 GROUND = vec3(0.616, 0.596, 0.549);
// 平行光はWeb版の sun.position (2.2, 4.5, 3.2) の方向から
const vec3 SUN_DIR = vec3(2.2, 4.5, 3.2) / 5.943904440685432;
void main() {
    vec3 n = normalize(gl_FrontFacing ? vNormal : -vNormal);
    vec3 hemi = mix(GROUND, SKY, 0.5 * n.y + 0.5) * 0.75;
    vec3 sun = vec3(max(dot(n, SUN_DIR), 0.0) * 0.85);
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
