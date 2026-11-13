#version 330
#extension GL_ARB_separate_shader_objects : require

// Passes a rounded-box description through to the fragment stage. It arrives in vertex attributes the game
// already has (see QuadGeometry): UV0 = position relative to the box centre, UV1 = half size,
// UV2 = (radius, stroke), Normal.x = feather. Lengths in UV1/UV2 are in 1/8 GUI pixel.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

layout(location = 0) out vec2 local;
layout(location = 1) out vec4 vertexColor;
layout(location = 2) flat out vec4 shape;
layout(location = 3) flat out float feather;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    local = UV0;
    vertexColor = Color;
    shape = vec4(vec2(UV1), vec2(UV2)) / 8.0;
    feather = Normal.x * 127.0;
}
