#version 330
#extension GL_ARB_separate_shader_objects : require

// From 26.3 the game links stages by explicit location; these match core/position_tex_color.vsh.
// Quarter-disc with an anti-aliased edge. UV (0,0) is the disc centre, |UV| = 1 the rim, so a rounded
// rectangle needs no uniforms of its own: four of these quads plus plain fills.
layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    float dist = length(texCoord0);
    float edge = fwidth(dist);
    float coverage = 1.0 - smoothstep(1.0 - edge, 1.0, dist);
    if (coverage <= 0.0) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb, vertexColor.a * coverage);
}
