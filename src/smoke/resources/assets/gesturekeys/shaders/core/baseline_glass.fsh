#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform vec2 ScreenSize;
uniform vec2 GuiSize;
uniform vec4 Rect;
uniform float Radius;
uniform float Kind;
uniform float Highlight;
uniform float Strength;
uniform float Progress;
out vec4 fragColor;

vec3 gaussian(sampler2D source, vec2 uv, vec2 stepSize) {
    vec3 color = vec3(0.0);
    float totalWeight = 0.0;
    for (int i=-6;i<=6;i++) {
        float weight = exp(-float(i*i)/12.5);
        color += texture(source,clamp(uv+float(i)*stepSize,vec2(.001),vec2(.999))).rgb*weight;
        totalWeight += weight;
    }
    return color/totalWeight;
}
vec3 blurred(vec2 uv) {
    return texture(Sampler2,clamp(uv,vec2(.001),vec2(.999))).rgb;
}
void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    if (Kind > 1.5) {
        // Two separable passes produce a continuous blur instead of sparse repeated edges.
        float stepSize = 1.35*Strength/GuiSize.y;
        vec3 color = Kind < 2.5
            ? gaussian(Sampler0,uv,vec2(stepSize*ScreenSize.y/ScreenSize.x,0.0))
            : gaussian(Sampler1,uv,vec2(0.0,stepSize));
        fragColor = vec4(color,1.0);
        return;
    }
    vec2 p = vec2(uv.x, 1.0-uv.y) * GuiSize;
    if (Kind < .5) {
        vec2 delta = p - GuiSize*.5;
        float distanceFromCenter = length(delta);
        float maxRadius = length(GuiSize*.5)+12.0;
        float reveal = 1.0-smoothstep(maxRadius*Progress-12.0,maxRadius*Progress,distanceFromCenter);
        float wave = exp(-abs(distanceFromCenter-maxRadius*Progress)/18.0)*(1.0-Progress);
        vec2 offset = normalize(delta+vec2(.001))*wave*7.0*Strength/GuiSize;
        offset.y = -offset.y;
        vec3 c = blurred(uv+offset);
        float vignette = smoothstep(.1,.9,length((p-GuiSize*.5)/GuiSize));
        c = mix(c, vec3(.055,.11,.16), .25+vignette*.12);
        c += vec3(.025,.06,.075)*(1.0-p.y/GuiSize.y);
        fragColor = vec4(c, reveal*.97);
        return;
    }
    float r = min(Radius,min(Rect.z,Rect.w)*.5);
    vec2 q = abs(p-(Rect.xy+Rect.zw*.5))-(Rect.zw*.5-vec2(r));
    float sd = length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;
    float alpha = 1.0-smoothstep(-.7,.7,sd);
    if (alpha <= .001) discard;
    vec2 normal = normalize(vec2(dFdx(sd),-dFdy(sd))+vec2(.00001));
    float edge = exp(-max(-sd,0.0)/5.5);
    vec2 offset = normal*edge*3.6*Strength/GuiSize;
    offset.y = -offset.y;
    vec3 c = blurred(uv+offset);
    float red = texture(Sampler0,clamp(uv+offset*1.17,vec2(.001),vec2(.999))).r;
    float blue = texture(Sampler0,clamp(uv+offset*.83,vec2(.001),vec2(.999))).b;
    c.rb = mix(c.rb,vec2(red,blue),edge*.18);
    c = mix(c,vec3(.075,.14,.20),.48);
    c += vec3(.05,.10,.12)*(Highlight*.65+.1);
    float rim = exp(-abs(sd+.85)*1.65);
    float lighting = clamp(dot(normal,normalize(vec2(-.6,-1.0))),0.0,1.0);
    c += vec3(.47,.78,.90)*rim*(.17+.40*lighting);
    float sheen = pow(clamp(1.0-(p.y-Rect.y)/max(Rect.z+Rect.w,1.0),0.0,1.0),8.0);
    c += vec3(.06,.085,.10)*sheen;
    fragColor = vec4(c,alpha*.96);
}
