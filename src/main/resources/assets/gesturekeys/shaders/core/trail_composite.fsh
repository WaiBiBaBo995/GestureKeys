#version 150
in vec2 localUV;
uniform sampler2D Sampler0;
out vec4 fragColor;
void main() { fragColor=texture(Sampler0,localUV); }
