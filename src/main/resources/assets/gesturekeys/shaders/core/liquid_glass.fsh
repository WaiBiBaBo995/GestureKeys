#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform vec2 RenderSize;
uniform vec2 GuiSize;
uniform vec4 Rect;
uniform float Radius;
uniform float Kind;
uniform float Highlight;
uniform float Strength;
uniform float Progress;
uniform float Style;
uniform vec3 Tint;
uniform float Time;
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
float grain(vec2 p) {
    return fract(sin(dot(floor(p),vec2(127.1,311.7)))*43758.5453)-.5;
}
vec3 hueShift(vec3 c) { return mix(c.gbr,vec3(.2,.25,1.0),.25); }
void main() {
    vec2 uv = gl_FragCoord.xy / RenderSize;
    int style = int(Style+.5);
    float blur = style==1 ? 1.65 : style==2 ? .65 : style==3 ? 1.35
        : style==4 ? 2.2 : style==5 ? 1.1 : style==8 ? .45 : 1.0;
    if (Kind > 1.5) {
        float stepSize = 1.35*Strength*blur/GuiSize.y;
        vec3 color = Kind < 2.5
            ? gaussian(Sampler0,uv,vec2(stepSize*RenderSize.y/RenderSize.x,0.0))
            : gaussian(Sampler1,uv,vec2(0.0,stepSize));
        fragColor = vec4(color,1.0);
        return;
    }
    vec2 p = vec2(uv.x,1.0-uv.y)*GuiSize;
    bool background = Kind<.5;
    float sd = -100.0;
    vec2 normal = vec2(0.0);
    float alpha = 1.0, edge = 0.0, reveal = 1.0;
    if (background) {
        float maxRadius = length(GuiSize*.5)+12.0;
        reveal = 1.0-smoothstep(maxRadius*Progress-12.0,maxRadius*Progress,length(p-GuiSize*.5));
    } else {
        float r=min(Radius,min(Rect.z,Rect.w)*.5);
        vec2 q=abs(p-(Rect.xy+Rect.zw*.5))-(Rect.zw*.5-vec2(r));
        sd=length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;
        alpha=1.0-smoothstep(-.7,.7,sd);
        if (alpha<=.001) discard;
        normal=normalize(vec2(dFdx(sd),-dFdy(sd))+vec2(.00001));
        edge=exp(-max(-sd,0.0)/5.5);
    }
    float refraction=style==0?1.0:style==1?.12:style==2?.6:style==3?.2
        :style==4?0.0:style==5?.35:style==8?2.2:.65;
    vec2 offset=normal*edge*3.6*Strength*refraction/GuiSize;
    offset.y=-offset.y;
    if (background && style==0) {
        vec2 delta=p-GuiSize*.5;
        float wave=exp(-abs(length(delta)-(length(GuiSize*.5)+12.0)*Progress)/18.0)*(1.0-Progress);
        offset=normalize(delta+vec2(.001))*wave*7.0*Strength/GuiSize*vec2(1.0,-1.0);
    }
    vec3 base=texture(Sampler2,clamp(uv+offset,vec2(.001),vec2(.999))).rgb;
    vec3 tint=Tint;
    float density=background?.26:.48, rimPower=1.0, grainAmount=0.0;
    if (style==1) { density=background?.42:.65; grainAmount=.025; rimPower=.35; }
    else if (style==2) { density=background?.17:.25; rimPower=1.3; }
    else if (style==3) { density=background?.5:.72; grainAmount=.045; rimPower=.6; }
    else if (style==4) { density=background?.86:.94; grainAmount=.015; rimPower=.25; }
    else if (style==5) { density=background?.66:.76; tint*=.22; rimPower=.65; }
    else if (style==6) { tint=mix(Tint,hueShift(Tint),clamp((p.x/GuiSize.x+p.y/GuiSize.y)*.5,0.0,1.0)); density=background?.4:.57; }
    else if (style==7) {
        float band=sin(p.x/GuiSize.x*5.0+p.y/GuiSize.y*3.0+Time*.35)*.5+.5;
        tint=mix(Tint,hueShift(Tint),band); density=background?.43:.56;
        base+=tint*pow(band,4.0)*.09;
    } else if (style==8) {
        density=background?.17:.22; rimPower=1.8;
        float facet=abs(fract((p.x+p.y*.6)/110.0)-.5);
        base+=tint*smoothstep(.43,.5,facet)*.07;
    }
    if (style==0 || style==8) {
        vec2 d=offset*(style==8?.38:.17);
        vec3 dispersed=vec3(texture(Sampler0,clamp(uv+offset+d,vec2(.001),vec2(.999))).r,
            base.g,texture(Sampler0,clamp(uv+offset-d,vec2(.001),vec2(.999))).b);
        base=mix(base,dispersed,edge*(style==8?.45:.18));
    }
    vec3 ink=tint*.19+vec3(.012);
    vec3 c=mix(base,ink,density);
    c+=grain(p)*grainAmount;
    if (background) c+=tint*.035*(1.0-p.y/GuiSize.y);
    else {
        float rim=exp(-abs(sd+.85)*1.65);
        float lighting=clamp(dot(normal,normalize(vec2(-.6,-1.0))),0.0,1.0);
        c+=mix(tint,vec3(1.0),.25)*rim*(.17+.4*lighting)*rimPower;
        c+=tint*(Highlight*.045+.006);
    }
    fragColor=vec4(clamp(c,0.0,1.0),alpha*reveal*.97);
}
