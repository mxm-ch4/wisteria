#version 330

// Quarter-disc with an anti-aliased edge. UV (0,0) is the disc centre, |UV| = 1 the rim, so a rounded
// rectangle needs no uniforms of its own: four of these quads plus plain fills.
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float dist = length(texCoord0);
    float edge = fwidth(dist);
    float coverage = 1.0 - smoothstep(1.0 - edge, 1.0, dist);
    if (coverage <= 0.0) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb, vertexColor.a * coverage);
}
