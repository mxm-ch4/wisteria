#version 330

// Passes a rounded-box description through to the fragment stage. It arrives in vertex attributes the game
// already has (see QuadGeometry): UV0 = position relative to the box centre, UV1 = half size,
// UV2 = (radius, stroke), Normal.x = feather. Lengths in UV1/UV2 are in 1/8 GUI pixel.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec2 local;
out vec4 vertexColor;
flat out vec4 shape;
flat out float feather;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    local = UV0;
    vertexColor = Color;
    shape = vec4(vec2(UV1), vec2(UV2)) / 8.0;
    feather = Normal.x * 127.0;
}
