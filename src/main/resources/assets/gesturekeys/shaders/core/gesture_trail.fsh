#version 150
in vec2 localUV;
uniform vec3 TrailColor;
uniform float Core;
uniform float Glow;
out vec4 fragColor;
void main() {
    float d=length(localUV);
    float aa=max(fwidth(d),.025);
    float core=1.0-smoothstep(Core-aa,Core+aa,d);
    float halo=exp(-d*d*5.0)*(1.0-smoothstep(.75,1.0,d))*.24*Glow;
    float coverage=max(core*.96,halo);
    fragColor=vec4(TrailColor*coverage,coverage);
}
