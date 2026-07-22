package com.abhinav.ownapp;

import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class Sphere {
    // 1. The Shaders (C-style code that runs directly on the GPU)
    private final String vertexShaderCode =
            "uniform mat4 uMVPMatrix;" +
                    "attribute vec4 vPosition;" +
                    "attribute vec2 aTexCoordinate;" +
                    "varying vec2 vTexCoordinate;" +
                    "void main() {" +
                    "  gl_Position = uMVPMatrix * vPosition;" +
                    "  vTexCoordinate = aTexCoordinate;" +
                    "}";

    private final String fragmentShaderCode =
            "precision mediump float;" +
                    "uniform sampler2D uTexture;" +
                    "varying vec2 vTexCoordinate;" +
                    "void main() {" +
                    "  gl_FragColor = texture2D(uTexture, vTexCoordinate);" +
                    "}";

    private FloatBuffer vertexBuffer;
    private FloatBuffer textureBuffer;
    private ShortBuffer drawListBuffer;
    private int mProgram;
    private int numIndices;

    public Sphere(float radius, int latitudeBands, int longitudeBands) {
        // 2. Mathematical generation of a 3D sphere using Trigonometry
        int numVertices = (latitudeBands + 1) * (longitudeBands + 1);
        float[] vertices = new float[numVertices * 3];
        float[] textureCoords = new float[numVertices * 2];
        short[] indices = new short[latitudeBands * longitudeBands * 6];

        int vertexIndex = 0;
        int texIndex = 0;

        for (int lat = 0; lat <= latitudeBands; lat++) {
            float theta = lat * (float) Math.PI / latitudeBands;
            float sinTheta = (float) Math.sin(theta);
            float cosTheta = (float) Math.cos(theta);

            for (int lon = 0; lon <= longitudeBands; lon++) {
                float phi = lon * 2 * (float) Math.PI / longitudeBands;
                float sinPhi = (float) Math.sin(phi);
                float cosPhi = (float) Math.cos(phi);

                float x = cosPhi * sinTheta;
                float y = cosTheta;
                float z = sinPhi * sinTheta;
                float u = 1.0f - ((float) lon / longitudeBands);
                float v = (float) lat / latitudeBands; // Flips the texture coordinate vertically

                vertices[vertexIndex++] = radius * x;
                vertices[vertexIndex++] = radius * y;
                vertices[vertexIndex++] = radius * z;
                textureCoords[texIndex++] = u;
                textureCoords[texIndex++] = v;
            }
        }

        int index = 0;
        for (int lat = 0; lat < latitudeBands; lat++) {
            for (int lon = 0; lon < longitudeBands; lon++) {
                short first = (short) ((lat * (longitudeBands + 1)) + lon);
                short second = (short) (first + longitudeBands + 1);

                indices[index++] = first;
                indices[index++] = second;
                indices[index++] = (short) (first + 1);
                indices[index++] = second;
                indices[index++] = (short) (second + 1);
                indices[index++] = (short) (first + 1);
            }
        }
        numIndices = indices.length;

        // 3. Load generated arrays into GPU-readable buffers
        vertexBuffer = ByteBuffer.allocateDirect(vertices.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer.put(vertices).position(0);

        textureBuffer = ByteBuffer.allocateDirect(textureCoords.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        textureBuffer.put(textureCoords).position(0);

        drawListBuffer = ByteBuffer.allocateDirect(indices.length * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
        drawListBuffer.put(indices).position(0);

        // 4. Compile shaders and link them to the OpenGL program
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        mProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mProgram, vertexShader);
        GLES20.glAttachShader(mProgram, fragmentShader);
        GLES20.glLinkProgram(mProgram);
    }

    public void draw(float[] mvpMatrix, int textureId) {
        GLES20.glUseProgram(mProgram);

        // Map the Position array
        int positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        // Map the Texture Coordinate array
        int texCoordHandle = GLES20.glGetAttribLocation(mProgram, "aTexCoordinate");
        GLES20.glEnableVertexAttribArray(texCoordHandle);
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer);

        // Map the Matrix
        int mvpMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);

        // Bind the earth map texture
        int textureHandle = GLES20.glGetUniformLocation(mProgram, "uTexture");
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(textureHandle, 0);

        // COMMAND THE GPU TO DRAW!
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, numIndices, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(texCoordHandle);
    }

    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        return shader;
    }
}