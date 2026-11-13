#version 330
#extension GL_ARB_separate_shader_objects : require

// A rounded box as a signed distance field: one quad gives a fill, an outline or a soft shadow/glow,
// all with a smooth edge at any scale.
layout(location = 0) in vec2 local;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) flat in vec4 shape;
layout(location = 3) flat in float feather;

layout(location = 0) out vec4 fragColor;

float roundedBox(vec2 point, vec2 halfSize, float radius) {
    vec2 q = abs(point) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

void main() {
    float dist = roundedBox(local, shape.xy, shape.z);
    float edge = max(fwidth(dist), 0.0001);
    float coverage;
    if (feather > 0.5) {
        coverage = 1.0 - smoothstep(-feather, feather, dist);
    } else if (shape.w > 0.0) {
        coverage = clamp(0.5 - (abs(dist + shape.w * 0.5) - shape.w * 0.5) / edge, 0.0, 1.0);
    } else {
        coverage = clamp(0.5 - dist / edge, 0.0, 1.0);
    }

    if (coverage <= 0.0) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb, vertexColor.a * coverage);
}
