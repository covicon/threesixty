package io.github.covicon.threesixty;

import static io.github.covicon.threesixty.Matrix.*;
import static java.lang.Double.*;
import static java.lang.Math.*;
import static java.lang.Math.max;
import static org.teavm.jso.webgl.WebGLRenderingContext.*;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSMethod;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.html.HTMLInputElement;
import org.teavm.jso.typedarrays.Float32Array;
import org.teavm.jso.typedarrays.Uint16Array;
import org.teavm.jso.webgl.WebGLProgram;
import org.teavm.jso.webgl.WebGLRenderingContext;
import org.teavm.jso.webgl.WebGLShader;
import org.teavm.jso.webgl.WebGLUniformLocation;



public class PanoramicImageViewer {

	/////////////////////////// EXTENDED HTML ELEMENT INTERFACES ////////////////////////////////////////// 
	
	interface MouseEvent extends org.teavm.jso.dom.events.MouseEvent {
		@JSProperty short getButtons();
	}
	
	interface Touch extends JSObject {
		@JSProperty double getScreenX();
		@JSProperty double getScreenY();
		
		@JSProperty int getIdentifier();
	}
	
	interface TouchList extends JSObject {
		@JSProperty int getLength();
		@JSMethod Touch item(int index);
	}
	
	interface TouchEvent extends Event {
		@JSProperty TouchList getTouches();
		@JSProperty TouchList getChangedTouches();
	}
	
	interface HTMLImageElement extends org.teavm.jso.dom.html.HTMLImageElement {
		@JSProperty
		boolean isComplete();
	}

	
	@JSBody(params = "identifier", script = "return window[identifier];")
	private static final native JSObject window(String identifier);

	
	
	////////////////////////////// HTML ELEMENTS //////////////////////////////////////////////////////////
	
	static protected final Window window = Window.current();
	static protected final HTMLDocument document = window.getDocument().cast();

	HTMLInputElement fovSlider = document.getElementById("fovSlider").cast();
	HTMLInputElement posSlider = document.getElementById("posSlider").cast();
	HTMLInputElement infoChecker = document.getElementById("infoChecker").cast();
	HTMLElement statusLabel = document.getElementById("status");
	
	HTMLImageElement texture = document.getElementById("texture").cast();
	HTMLCanvasElement screen = document.getElementById("viewer").cast();
	WebGLRenderingContext gl = screen.getContext("webgl").cast();
	
	HTMLInputElement controls[] = { fovSlider, posSlider, infoChecker }; 
	
	
	
	//////////////////////////////// RENDERING LOGIC //////////////////////////////////////////////////////
	static final float WORLD_SCALE = 100;

	static final String vtxSource = "" +
			"#version 100\n" +
			"precision mediump float;\n" +
			"uniform float pointSize;" +
			"uniform mat4 matrix;" +
			"attribute vec4 pointCoord;" + 
			"attribute vec4 uvCoord;" +
			"varying vec4 uv;" +
			"void main() {" + 
			"  vec4 v = matrix*pointCoord;" +
			"  gl_PointSize = pointSize/(v.w);" +
			"  gl_Position = v;"
			+ "uv = uvCoord;" + 
			"}";
	
	static final String fragSource = ""+
			"#version 100\n" +
			"precision mediump float;" + 
			"uniform vec3 color;"+
			"uniform sampler2D sampler;"+
			"varying vec4 uv; "+
			"void main() {" +
			"  gl_FragColor = vec4(color.rgb, 1.0)*texture2D(sampler, uv.xy);" + 
			"}";
	
	
	
//	int latitudes = 150, longitudes = 2*latitudes;
	int latitudes = 50, longitudes = 2*latitudes;
	int vertices = (latitudes+1)*(longitudes+1);
	double r = 1;

	final WebGLUniformLocation matrixLoc;
	final WebGLUniformLocation pointSizeLoc;
	final WebGLUniformLocation colorLoc;
	final WebGLUniformLocation samplerLoc;
	
	
	private void generateSphere( int latitudes, int longitudes,
			Float32Array xyzCoords, Float32Array uvCoords, Uint16Array elements) 
	{
		int vertexCount = (latitudes+1)*(longitudes+1);
		for (int latitude = 0, count = 0; latitude<=latitudes; latitude++)
			for (int longitude = 0; longitude<=longitudes; longitude++, count++) {
				double u = longitude*1d/longitudes;
				double v = latitude*1d/latitudes;
				
				double theta = 2*PI*u;  // [-180°, +180°]
				double phi = PI*v; // [-90°, +90°]  
				
				double uv[] = {  u, v };
				double xyz[] = { 
					r*sin(phi)*cos(theta),
					r*sin(phi)*sin(theta),
					r*cos(phi)
				};

				xyzCoords.set(xyz, count*3);
				uvCoords.set(uv, count*2);
				
				int face[] = {
					count%vertexCount, (count+1)%vertexCount, (count+1+longitudes)%vertexCount,
					count%vertexCount, (count+1+longitudes)%vertexCount, (count+longitudes)%vertexCount
				};
				
				elements.set(face, count*6);
			}
	}
	
	
	private void resizeAndRepaint() {
//		screen.setWidth(window.getInnerWidth());
//		screen.setHeight(window.getInnerHeight());
		screen.setWidth(screen.getClientWidth());
		screen.setHeight(screen.getClientHeight());
		repaint();
	}
	

	
	
	private void loadTexture() {
		gl.texImage2D(TEXTURE_2D, 0, RGB, RGB, UNSIGNED_BYTE, texture);
		gl.texParameteri(TEXTURE_2D, TEXTURE_MIN_FILTER, LINEAR);
		gl.texParameteri(TEXTURE_2D, TEXTURE_MAG_FILTER, LINEAR);
		gl.texParameteri(TEXTURE_2D, TEXTURE_WRAP_S, CLAMP_TO_EDGE);
		gl.texParameteri(TEXTURE_2D, TEXTURE_WRAP_T, CLAMP_TO_EDGE);
		textureResident = true;
		
		resizeAndRepaint();
		screen.getStyle().setProperty("opacity", "100");		
	}
	
	
	long start = System.nanoTime(), frame = 0;
	float[] M = {1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};

	
	double rotateX = 0, rotateY = 0;
	
	
	private void repaint() {
		if (!textureResident)
			return;
		
		frame++;
		statusLabel.setInnerHTML("Frame: "+frame);
		
		int width = gl.getDrawingBufferWidth(), height = gl.getDrawingBufferHeight();
		gl.viewport(0, 0, width, height);
		
		gl.clearColor(0.1f, 0.2f, 0.3f, 1);
		gl.clear(COLOR_BUFFER_BIT | DEPTH_BUFFER_BIT);
		
		
		float a = height*1f/width;

		float near = (float)parseDouble(fovSlider.getValue())*0.005f;
		float pos = (float)parseDouble(posSlider.getValue())*0.5f;
		
		identity(M);
		frustum(M,  -1, +1, -a, +a, 1f-near, 10000);
		translate(M, 0,0, -pos);
		rotate(M, rotateX, 1, 0, 0);
		rotate(M, rotateY, 0, 1, 0);
		rotate(M, PI/2, 1, 0, 0);
		scale(M, WORLD_SCALE,WORLD_SCALE,WORLD_SCALE);
		
		gl.enable(DEPTH_TEST);

		gl.uniform1i(samplerLoc, 0);
		gl.uniformMatrix4fv(matrixLoc, false, M);
		gl.uniform1f(pointSizeLoc, max(width, height)/100);
		
		gl.uniform3f(colorLoc, 1,1,1);
		gl.drawElements(TRIANGLES, vertices*6, UNSIGNED_SHORT, 0);
		

		gl.disable(DEPTH_TEST);
		gl.enable(CULL_FACE);
		gl.cullFace(FRONT);
		
		gl.uniform3f(colorLoc, 0,0,0);
		if (infoChecker.isChecked()) 
			gl.drawElements(LINES, vertices*6, UNSIGNED_SHORT, 0);
		
		
		boolean momentum = abs(rotationSpeedX)+abs(rotationSpeedY)>0.0001 ;
		if ( glide && momentum || frame<2)
			Window.requestAnimationFrame( e -> repaint() );
		
		if (!momentum)
			window.getLocation().setHash(rotateX+","+rotateY+","+fovSlider.getValue()+","+posSlider.getValue());
	}
	

	
	
	
	//////////////////////////////// INTERACTION LOGIC //////////////////////////////////////////////////////
	
	double rotationSpeedX = 0, rotationSpeedY = 0;
	double lastX = 0, lastY = 0;

	boolean glide = false;
	int trackedId = -1;
	

	private void endInteraction(Event e) {
		trackedId = -1;
		glide = true;
		repaint();
	}

	private void startInteraction(Event e) {
		glide = false;
		rotationSpeedX = rotationSpeedY = 0;
	}
	
	private void startTouch(Event e) {
		Touch t = TouchEvent.class.cast(e).getChangedTouches().item(0);
		trackedId = t.getIdentifier();
		lastX = t.getScreenX();
		lastY = t.getScreenY();
		startInteraction(e);
	}
	
	private void mouseMove(Event ge) {
		MouseEvent e = ge.cast(); 
		e.preventDefault();
		if (e.getButtons()!=0) {
			rotateY += rotationSpeedY = -e.getMovementX()*2/screen.getWidth();
			rotateX += rotationSpeedX = +e.getMovementY()*2/screen.getHeight();
			repaint();
		}
	}
	
	private void touchMove(Event ge) {
		TouchEvent e = ge.cast();
		e.preventDefault();
		TouchList tl = e.getTouches();
		for (int i=0,n=tl.getLength();i<n;i++) {
			Touch t = tl.item(i);
			if (t.getIdentifier()!=trackedId) 
				continue;
			
			double currentX = t.getScreenX();
			double currentY = t.getScreenY();
			
			int size = (screen.getHeight()+screen.getWidth())/2;
			rotateY += rotationSpeedY = -(currentX - lastX)/size;
			rotateX += rotationSpeedX = +(currentY - lastY)/size;
			
			lastX = currentX;
			lastY = currentY;
		}
		repaint();
	}
	
	
	
	
	//////////////////////////////// STARTUP LOGIC //////////////////////////////////////////////////////

	public PanoramicImageViewer() {
		
		try {
			String values[] = (window.getLocation().getHash()+",").substring(1).split(",");
			rotateX = parseDouble(values[0]);
			rotateY = parseDouble(values[1]);
			fovSlider.setValue(values[2]);
			posSlider.setValue(values[3]);
		} catch (Exception ex) {}
		
		
		
		window.addEventListener("resize", e->resizeAndRepaint() );
		
		screen.addEventListener("mousemove", this::mouseMove);
		screen.addEventListener("mousedown", this::startInteraction );
		screen.addEventListener("mouseup", this::endInteraction );
		screen.addEventListener("touchstart", this::startTouch);
		screen.addEventListener("touchend", this::endInteraction);
		screen.addEventListener("touchmove", this::touchMove);
		
		
		for (HTMLInputElement slider: controls) {
			slider.addEventListener("dblclick", e-> ((HTMLInputElement)e.getTarget()).setValue("0") );
			slider.addEventListener("input", e-> repaint() );
		}
		
		
		
		////////////////
		
		WebGLProgram prog = linkNewProgram(gl,
			compileNewShader(gl, VERTEX_SHADER, vtxSource ),
			compileNewShader(gl, FRAGMENT_SHADER, fragSource )
		);
		
		this.matrixLoc = gl.getUniformLocation(prog, "matrix");
		this.pointSizeLoc = gl.getUniformLocation(prog, "pointSize");
		this.colorLoc = gl.getUniformLocation(prog, "color");
		this.samplerLoc = gl.getUniformLocation(prog, "sampler");
		
		gl.useProgram(prog);
		
		Float32Array xyzCoords = Float32Array.create(vertices*3);
		Float32Array uvCoords = Float32Array.create(vertices*2);
		Uint16Array elements = Uint16Array.create(vertices*6);
		
		generateSphere(latitudes, longitudes, xyzCoords, uvCoords, elements);
		bindNewArrayBuffer(gl, prog, "pointCoord", xyzCoords, 3);
		bindNewArrayBuffer(gl, prog, "uvCoord", uvCoords, 2);
		
		gl.bindBuffer(ELEMENT_ARRAY_BUFFER, gl.createBuffer());
		gl.bufferData(ELEMENT_ARRAY_BUFFER, elements.getBuffer(), STATIC_DRAW);

		
		gl.bindTexture(TEXTURE_2D, gl.createTexture());
		texture.addEventListener("load", e ->  loadTexture() );
		
		if (texture.isComplete() && texture.getNaturalWidth()>0)
			loadTexture();

		texture.setSrc(window.getLocation().getSearch().replaceAll("^\\?", ""));
//		texture.setSrc("https://live.staticflickr.com/65535/47982384422_99017a408a_k_d.jpg");

		Window.setInterval(()->tick(), 1);
		
		resizeAndRepaint();
	}
	
	boolean textureResident = false;
	
	
	private void tick() {
		if (glide) {
			rotateX+=rotationSpeedX*=0.98765;
			rotateY+=rotationSpeedY*=0.98765;
		}
	}
	

	
	
	/////////////////////////////////////// LAUNCH ////////////////////////////////////////////////////////////
	
	public static void main(String[] args) {
		new PanoramicImageViewer();
	}
	
	
	
	
	
	

	
	
	
	
	
	
	
	/////////////////////////////////////// STATIC HELPER METHODS /////////////////////////////////////////////

	public static WebGLShader compileNewShader(WebGLRenderingContext gl, int type, String source) {
		WebGLShader vsh = gl.createShader(type);
		gl.shaderSource(vsh, source);
		gl.compileShader(vsh);
		if (!gl.getShaderParameterb(vsh, COMPILE_STATUS))
			throw new RuntimeException(gl.getShaderInfoLog(vsh));
		
		return vsh;
	}
	
	public static WebGLProgram linkNewProgram(WebGLRenderingContext gl, WebGLShader... shaders ) {
		WebGLProgram prog = gl.createProgram();
		for (WebGLShader shader: shaders)
			gl.attachShader(prog, shader);
		
		gl.linkProgram(prog);
		if (!gl.getProgramParameterb(prog, LINK_STATUS))
			throw new RuntimeException(gl.getProgramInfoLog(prog));
		
		return prog;
	}
	
	public static void bindNewArrayBuffer(WebGLRenderingContext gl, WebGLProgram prog, String attrib, Float32Array a, int dim) {
		enableNewArrayBuffer(gl, prog, attrib, a, dim, 0, 0);
	}
	
	public static void enableNewArrayBuffer(WebGLRenderingContext gl, WebGLProgram prog, String attrib, Float32Array a, int dim, int stride, int offset) {
		int aloc = gl.getAttribLocation(prog, attrib);
		gl.bindBuffer(ARRAY_BUFFER, gl.createBuffer());
		gl.bufferData(ARRAY_BUFFER, a, STATIC_DRAW);
		gl.enableVertexAttribArray(aloc);
		gl.vertexAttribPointer(aloc, dim, FLOAT, false, stride, offset);
	}
}


