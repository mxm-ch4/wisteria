#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec3 viewNormal;
in vec3 viewPosition;

out vec4 fragColor;

void main() {
#ifdef RIM
    // Strongest where the surface turns away from the camera: the silhouette glows, the middle stays faint.
    vec3 n = normalize(viewNormal);
    vec3 v = normalize(-viewPosition);
    float rim = 1.0 - abs(dot(n, v));
    float alpha = vertexColor.a * (0.12 + 0.88 * rim * rim);
    fragColor = vec4(vertexColor.rgb, alpha);
#else
    fragColor = vertexColor;
#endif
    if (fragColor.a < 0.004) {
        discard;
    }
}
