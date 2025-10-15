package com.prism.plyviewer360;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Professional OpenGL ES 2.0 renderer for PLY models
 * Supports vertex colors, lighting, and smooth 360° rotation
 */
public class PLYRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "PLYRenderer";

    // Shader program
    private int shaderProgram;

    // Vertex buffer objects
    private int[] vbo = new int[3]; // positions, colors, normals
    private int ebo; // element buffer (indices)

    // Model data
    private int indexCount = 0;
    private boolean modelLoaded = false;

    // Matrices
    private float[] modelMatrix = new float[16];
    private float[] viewMatrix = new float[16];
    private float[] projectionMatrix = new float[16];
    private float[] mvpMatrix = new float[16];
    private float[] normalMatrix = new float[16];

    // Rotation angles for 360° viewing
    private float rotationX = 0f;
    private float rotationY = 0f;
    private float autoRotationY = 0f;

    // Zoom level
    private float zoomLevel = 3.0f;

    // Auto-rotation
    private boolean autoRotate = true;

    // Shader sources
    private static final String VERTEX_SHADER =
            "#version 100\n" +
                    "attribute vec3 aPosition;\n" +
                    "attribute vec3 aColor;\n" +
                    "attribute vec3 aNormal;\n" +
                    "\n" +
                    "uniform mat4 uMVPMatrix;\n" +
                    "uniform mat4 uModelMatrix;\n" +
                    "uniform mat3 uNormalMatrix;\n" +
                    "\n" +
                    "varying vec3 vColor;\n" +
                    "varying vec3 vNormal;\n" +
                    "varying vec3 vFragPos;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    gl_Position = uMVPMatrix * vec4(aPosition, 1.0);\n" +
                    "    vColor = aColor;\n" +
                    "    vNormal = normalize(uNormalMatrix * aNormal);\n" +
                    "    vFragPos = vec3(uModelMatrix * vec4(aPosition, 1.0));\n" +
                    "}\n";

    private static final String FRAGMENT_SHADER =
            "#version 100\n" +
                    "precision mediump float;\n" +
                    "\n" +
                    "varying vec3 vColor;\n" +
                    "varying vec3 vNormal;\n" +
                    "varying vec3 vFragPos;\n" +
                    "\n" +
                    "uniform vec3 uLightPos;\n" +
                    "uniform vec3 uViewPos;\n" +
                    "\n" +
                    "void main() {\n" +
                    "    // Ambient\n" +
                    "    float ambientStrength = 0.3;\n" +
                    "    vec3 ambient = ambientStrength * vColor;\n" +
                    "\n" +
                    "    // Diffuse\n" +
                    "    vec3 norm = normalize(vNormal);\n" +
                    "    vec3 lightDir = normalize(uLightPos - vFragPos);\n" +
                    "    float diff = max(dot(norm, lightDir), 0.0);\n" +
                    "    vec3 diffuse = diff * vColor;\n" +
                    "\n" +
                    "    // Specular\n" +
                    "    float specularStrength = 0.5;\n" +
                    "    vec3 viewDir = normalize(uViewPos - vFragPos);\n" +
                    "    vec3 reflectDir = reflect(-lightDir, norm);\n" +
                    "    float spec = pow(max(dot(viewDir, reflectDir), 0.0), 32.0);\n" +
                    "    vec3 specular = specularStrength * spec * vec3(1.0);\n" +
                    "\n" +
                    "    vec3 result = ambient + diffuse + specular;\n" +
                    "    gl_FragColor = vec4(result, 1.0);\n" +
                    "}\n";

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // Set background color
        GLES20.glClearColor(0.1f, 0.1f, 0.15f, 1.0f);

        // Enable depth testing
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LESS);

        // Enable face culling
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);

        // Create shader program
        shaderProgram = createShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);

        if (shaderProgram == 0) {
            Log.e(TAG, "Failed to create shader program");
            return;
        }

        Log.d(TAG, "OpenGL initialized successfully");
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);

        // Calculate projection matrix
        float aspect = (float) width / height;
        Matrix.perspectiveM(projectionMatrix, 0, 45f, aspect, 0.1f, 100f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (!modelLoaded) {
            return;
        }

        // Update auto-rotation
        if (autoRotate) {
            autoRotationY += 0.5f;
            if (autoRotationY >= 360f) autoRotationY -= 360f;
        }

        // Set view matrix
        Matrix.setLookAtM(viewMatrix, 0,
                0f, 0f, zoomLevel,  // camera position
                0f, 0f, 0f,          // look at point
                0f, 1f, 0f);         // up vector

        // Build model matrix with rotations
        Matrix.setIdentityM(modelMatrix, 0);
        Matrix.rotateM(modelMatrix, 0, rotationX, 1f, 0f, 0f);
        Matrix.rotateM(modelMatrix, 0, rotationY + autoRotationY, 0f, 1f, 0f);

        // Calculate MVP matrix
        float[] tempMatrix = new float[16];
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0);

        // Calculate normal matrix (inverse transpose of model matrix)
        Matrix.invertM(normalMatrix, 0, modelMatrix, 0);
        Matrix.transposeM(normalMatrix, 0, normalMatrix, 0);

        // Use shader program
        GLES20.glUseProgram(shaderProgram);

        // Set uniforms
        int mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix");
        int modelMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uModelMatrix");
        int normalMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uNormalMatrix");
        int lightPosHandle = GLES20.glGetUniformLocation(shaderProgram, "uLightPos");
        int viewPosHandle = GLES20.glGetUniformLocation(shaderProgram, "uViewPos");

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);
        GLES20.glUniformMatrix4fv(modelMatrixHandle, 1, false, modelMatrix, 0);
        GLES20.glUniformMatrix3fv(normalMatrixHandle, 1, false, normalMatrix, 0);
        GLES20.glUniform3f(lightPosHandle, 5.0f, 5.0f, 5.0f);
        GLES20.glUniform3f(viewPosHandle, 0f, 0f, zoomLevel);

        // Bind vertex buffers
        int positionHandle = GLES20.glGetAttribLocation(shaderProgram, "aPosition");
        int colorHandle = GLES20.glGetAttribLocation(shaderProgram, "aColor");
        int normalHandle = GLES20.glGetAttribLocation(shaderProgram, "aNormal");

        // Position
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0]);
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, 0);

        // Color
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[1]);
        GLES20.glEnableVertexAttribArray(colorHandle);
        GLES20.glVertexAttribPointer(colorHandle, 3, GLES20.GL_FLOAT, false, 0, 0);

        // Normal
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[2]);
        GLES20.glEnableVertexAttribArray(normalHandle);
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, 0);

        // Draw
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_INT, 0);

        // Cleanup
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(colorHandle);
        GLES20.glDisableVertexAttribArray(normalHandle);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    /**
     * Load model data into GPU buffers
     */
    public void loadModel(PLYParser.ParseResult parseResult) {
        if (!parseResult.isValid()) {
            Log.e(TAG, "Invalid parse result");
            return;
        }

        // Normalize and center the model
        float[] vertices = normalizeVertices(parseResult.vertices);

        // Create buffers
        FloatBuffer vertexBuffer = createFloatBuffer(vertices);
        FloatBuffer colorBuffer = createFloatBuffer(parseResult.colors);
        FloatBuffer normalBuffer = createFloatBuffer(parseResult.normals);
        IntBuffer indexBuffer = createIntBuffer(parseResult.indices);

        indexCount = parseResult.indices.length;

        // Generate VBOs
        GLES20.glGenBuffers(3, vbo, 0);

        // Upload vertex positions
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
                vertices.length * 4, vertexBuffer, GLES20.GL_STATIC_DRAW);

        // Upload colors
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[1]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
                parseResult.colors.length * 4, colorBuffer, GLES20.GL_STATIC_DRAW);

        // Upload normals
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[2]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
                parseResult.normals.length * 4, normalBuffer, GLES20.GL_STATIC_DRAW);

        // Generate and upload EBO
        int[] eboArray = new int[1];
        GLES20.glGenBuffers(1, eboArray, 0);
        ebo = eboArray[0];

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER,
                parseResult.indices.length * 4, indexBuffer, GLES20.GL_STATIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        modelLoaded = true;
        Log.d(TAG, "Model loaded successfully with " + indexCount + " indices");
    }

    /**
     * Normalize vertices to fit in view and center at origin
     */
    private float[] normalizeVertices(float[] vertices) {
        if (vertices.length == 0) return vertices;

        // Find bounding box
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        for (int i = 0; i < vertices.length; i += 3) {
            minX = Math.min(minX, vertices[i]);
            maxX = Math.max(maxX, vertices[i]);
            minY = Math.min(minY, vertices[i + 1]);
            maxY = Math.max(maxY, vertices[i + 1]);
            minZ = Math.min(minZ, vertices[i + 2]);
            maxZ = Math.max(maxZ, vertices[i + 2]);
        }

        // Calculate center and scale
        float centerX = (minX + maxX) / 2f;
        float centerY = (minY + maxY) / 2f;
        float centerZ = (minZ + maxZ) / 2f;

        float rangeX = maxX - minX;
        float rangeY = maxY - minY;
        float rangeZ = maxZ - minZ;
        float maxRange = Math.max(rangeX, Math.max(rangeY, rangeZ));

        float scale = 2.0f / maxRange; // Normalize to fit in -1 to 1

        // Apply transformation
        float[] normalized = new float[vertices.length];
        for (int i = 0; i < vertices.length; i += 3) {
            normalized[i] = (vertices[i] - centerX) * scale;
            normalized[i + 1] = (vertices[i + 1] - centerY) * scale;
            normalized[i + 2] = (vertices[i + 2] - centerZ) * scale;
        }

        return normalized;
    }

    /**
     * Create shader program from vertex and fragment shader source
     */
    private int createShaderProgram(String vertexSource, String fragmentSource) {
        int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) return 0;

        int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (fragmentShader == 0) {
            GLES20.glDeleteShader(vertexShader);
            return 0;
        }

        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);

        if (linkStatus[0] == 0) {
            Log.e(TAG, "Program link error: " + GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);
            return 0;
        }

        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);

        return program;
    }

    /**
     * Compile a shader
     */
    private int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);

        if (compiled[0] == 0) {
            Log.e(TAG, "Shader compilation error: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }

        return shader;
    }

    /**
     * Helper to create FloatBuffer from float array
     */
    private FloatBuffer createFloatBuffer(float[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer buffer = bb.asFloatBuffer();
        buffer.put(data);
        buffer.position(0);
        return buffer;
    }

    /**
     * Helper to create IntBuffer from int array
     */
    private IntBuffer createIntBuffer(int[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
        bb.order(ByteOrder.nativeOrder());
        IntBuffer buffer = bb.asIntBuffer();
        buffer.put(data);
        buffer.position(0);
        return buffer;
    }

    // Public methods for controlling the view

    public void setRotation(float x, float y) {
        rotationX = x;
        rotationY = y;
    }

    public void addRotation(float dx, float dy) {
        rotationX += dx;
        rotationY += dy;

        // Clamp X rotation
        rotationX = Math.max(-90f, Math.min(90f, rotationX));
    }

    public void setZoom(float zoom) {
        zoomLevel = Math.max(1.5f, Math.min(10f, zoom));
    }

    public void addZoom(float delta) {
        zoomLevel += delta;
        zoomLevel = Math.max(1.5f, Math.min(10f, zoomLevel));
    }

    public void setAutoRotate(boolean enabled) {
        autoRotate = enabled;
    }

    public boolean isAutoRotate() {
        return autoRotate;
    }

    public void resetView() {
        rotationX = 0f;
        rotationY = 0f;
        autoRotationY = 0f;
        zoomLevel = 3.0f;
        autoRotate = true;
    }
}