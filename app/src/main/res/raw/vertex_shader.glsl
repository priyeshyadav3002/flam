// vertex_shader.glsl
attribute vec4 vPosition;
attribute vec2 vTexCoord;
varying vec2 fTexCoord;

void main() {
  fTexCoord = vTexCoord;
  gl_Position = vPosition;
}