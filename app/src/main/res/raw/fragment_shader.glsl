// fragment_shader.glsl
precision mediump float;
varying vec2 fTexCoord;
uniform sampler2D sTexture;

void main() {
  // Hum C++ se grayscale image bhej rahe hain (sirf R channel mein data hai)
  // Hum uss data ko R, G, aur B teeno mein daal rahe hain taaki woh white dikhe.
  gl_FragColor = vec4(texture2D(sTexture, fTexCoord).r,
                        texture2D(sTexture, fTexCoord).r,
                        texture2D(sTexture, fTexCoord).r,
                        1.0);
}