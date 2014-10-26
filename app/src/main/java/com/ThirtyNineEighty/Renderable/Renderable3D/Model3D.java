package com.ThirtyNineEighty.Renderable.Renderable3D;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import com.ThirtyNineEighty.Helpers.Vector3;
import com.ThirtyNineEighty.Renderable.Renderable;
import com.ThirtyNineEighty.Renderable.Shader;
import com.ThirtyNineEighty.Renderable.Shader3D;
import com.ThirtyNineEighty.System.GameContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.microedition.khronos.egl.EGLContext;

public class Model3D implements I3DRenderable
{
  private float[] modelProjectionViewMatrix;
  private float[] modelMatrix;
  private Vector3 position;

  private float xAngle;
  private float yAngle;
  private float zAngle;

  private int textureHandle;
  private int bufferHandle;
  private int numOfTriangles;

  private boolean needBuildMatrix;

  public Model3D(String geometryFileName, String textureFileName)
  {
    modelMatrix = new float[16];
    modelProjectionViewMatrix = new float[16];
    position = new Vector3();

    loadGeometry(geometryFileName);

    textureHandle = Renderable.loadTexture(textureFileName, true);

    needBuildMatrix = true;
  }

  @Override
  public void finalize() throws Throwable
  {
    super.finalize();

    GLES20.glDeleteBuffers(1, new int[] { bufferHandle }, 0);
    GLES20.glDeleteTextures(1, new int[] { textureHandle }, 0);
  }

  @Override
  public void draw(float[] projectionViewMatrix, float[] lightPosition)
  {
    // if modelMatrix need rebuilding - rebuild it
    tryBuildMatrix();

    // build result matrix
    Matrix.multiplyMM(modelProjectionViewMatrix, 0, projectionViewMatrix, 0, modelMatrix, 0);

    // bind texture to 0 slot
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle);

    // bind data buffer
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bufferHandle);

    Shader3D shader = (Shader3D) Shader.getCurrent();

    // send uniform data to shader
    GLES20.glUniform1i(shader.uniformTextureHandle, 0);
    GLES20.glUniformMatrix4fv(shader.uniformMatrixProjectionHandle, 1, false, modelProjectionViewMatrix, 0);
    GLES20.glUniformMatrix4fv(shader.uniformMatrixHandle, 1, false, modelMatrix, 0);
    GLES20.glUniform3fv(shader.uniformLightVectorHandle, 1, lightPosition, 0);

    // enable attribute arrays
    GLES20.glEnableVertexAttribArray(shader.attributePositionHandle);
    GLES20.glEnableVertexAttribArray(shader.attributeNormalHandle);
    GLES20.glEnableVertexAttribArray(shader.attributeTexCoordHandle);

    // set offsets to arrays for buffer
    GLES20.glVertexAttribPointer(shader.attributePositionHandle, 3, GLES20.GL_FLOAT, false, 32, 0);
    GLES20.glVertexAttribPointer(shader.attributeNormalHandle, 3, GLES20.GL_FLOAT, false, 32, 12);
    GLES20.glVertexAttribPointer(shader.attributeTexCoordHandle, 2, GLES20.GL_FLOAT, false, 32, 24);

    // validating if debug
    shader.validateProgram();

    // draw
    GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, numOfTriangles * 3);

    // disable attribute arrays
    GLES20.glDisableVertexAttribArray(shader.attributePositionHandle);
    GLES20.glDisableVertexAttribArray(shader.attributeNormalHandle);
    GLES20.glDisableVertexAttribArray(shader.attributeTexCoordHandle);
  }

  private void tryBuildMatrix()
  {
    if (!needBuildMatrix)
      return;

    Matrix.setIdentityM(modelMatrix, 0);
    Matrix.translateM(modelMatrix, 0, position.getX(), position.getY(), position.getZ());

    Matrix.rotateM(modelMatrix, 0, xAngle, 1.0f, 0.0f, 0.0f);
    Matrix.rotateM(modelMatrix, 0, yAngle, 0.0f, 1.0f, 0.0f);
    Matrix.rotateM(modelMatrix, 0, zAngle, 0.0f, 0.0f, 1.0f);

    needBuildMatrix = false;
  }

  private void loadGeometry(String fileName)
  {
    try
    {
      InputStream stream = GameContext.getAppContext().getAssets().open(fileName);

      int size = stream.available();
      byte[] data = new byte[size];
      stream.read(data);
      stream.close();

      ByteBuffer dataBuffer = ByteBuffer.allocateDirect(size - 4);
      dataBuffer.put(data, 4, size - 4);
      dataBuffer.order(ByteOrder.LITTLE_ENDIAN);
      dataBuffer.position(0);

      ByteBuffer numBuffer = ByteBuffer.allocateDirect(4);
      numBuffer.put(data, 0, 4);
      numBuffer.order(ByteOrder.LITTLE_ENDIAN);

      numOfTriangles = numBuffer.getInt(0);

      int[] buffers = new int[1];
      GLES20.glGenBuffers(1, buffers, 0);
      GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, buffers[0]);
      GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, dataBuffer.capacity(), dataBuffer, GLES20.GL_STATIC_DRAW);

      int error;
      if ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR)
        Log.e("Error", Integer.toString(error));

      bufferHandle = buffers[0];
    }
    catch(IOException e)
    {
      Log.e("Error", e.getMessage());
    }
  }

  @Override
  public Vector3 getPosition() { return position; }

  public void setPosition(Vector3 position)
  {
    this.position = position;

    needBuildMatrix = true;
  }

  public void move(float length)
  {
    Vector3 vector = new Vector3();
    float[] translateMatrix = new float[16];
    Matrix.setIdentityM(translateMatrix, 0);

    Matrix.rotateM(translateMatrix, 0, xAngle, 1.0f, 0.0f, 0.0f);
    Matrix.rotateM(translateMatrix, 0, yAngle, 0.0f, 1.0f, 0.0f);
    Matrix.rotateM(translateMatrix, 0, zAngle, 0.0f, 0.0f, 1.0f);

    Matrix.multiplyMV(vector.getRaw(), 0, translateMatrix, 0, Vector3.xAxis.getRaw(), 0);

    move(vector, length);
  }

  public void move(Vector3 vector, float length)
  {
    position.addToX(vector.getX() * length);
    position.addToY(vector.getY() * length);
    position.addToZ(vector.getZ() * length);

    needBuildMatrix = true;
  }

  @Override
  public float getXAngle() { return xAngle; }

  public void setXAngle(float value)
  {
    xAngle = value;
    needBuildMatrix = true;
  }

  @Override
  public float getYAngle() { return yAngle; }

  public void setYAngle(float value)
  {
    yAngle = value;
    needBuildMatrix = true;
  }

  @Override
  public float getZAngle() { return zAngle; }

  public void setZAngle(float value)
  {
    zAngle = value;
    needBuildMatrix = true;
  }

  public void rotateAboutX(float angle)
  {
    xAngle += angle;
    xAngle = CorrectAngle(xAngle);
    needBuildMatrix = true;
  }

  public void rotateAboutY(float angle)
  {
    yAngle += angle;
    yAngle = CorrectAngle(yAngle);
    needBuildMatrix = true;
  }

  public void rotateAboutZ(float angle)
  {
    zAngle += angle;
    zAngle = CorrectAngle(zAngle);
    needBuildMatrix = true;
  }

  private float CorrectAngle(float angle)
  {
    if (angle < 0.0f)
      angle += 360.0f;

    if (angle >= 360.0f)
      angle -= 360.0f;

    return angle;
  }
}
