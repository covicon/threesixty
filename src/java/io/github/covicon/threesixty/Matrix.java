package io.github.covicon.threesixty;

import static java.lang.Math.*;

import java.util.Arrays;

public class Matrix {
	static public void print(float[] A) {
		final float A00 = A[ 0], A10 = A[ 1], A20 = A[ 2], A30 = A[ 3];
		final float A01 = A[ 4], A11 = A[ 5], A21 = A[ 6], A31 = A[ 7];
		final float A02 = A[ 8], A12 = A[ 9], A22 = A[10], A32 = A[11];
		final float A03 = A[12], A13 = A[13], A23 = A[14], A33 = A[15];
		
		final float[][] M = {
				{ A00, A01, A02, A03 },
				{ A10, A11, A12, A13 },
				{ A20, A21, A22, A23 },
				{ A30, A31, A32, A33 },
		};
		
		System.out.println( Arrays.toString(M[0]) );
		System.out.println( Arrays.toString(M[1]) );
		System.out.println( Arrays.toString(M[2]) );
		System.out.println( Arrays.toString(M[3]) );
	}
	
	
	static public float[] identity(float[] matrix) {
		matrix[ 0] = matrix[ 5] = matrix[10] = matrix[15] = 1;
		
		matrix[ 1] = matrix[ 2] = matrix[ 3] = matrix[ 4] =
		matrix[ 6] = matrix[ 7] = matrix[ 8] = matrix[ 9] =
		matrix[11] = matrix[12] = matrix[13] = matrix[14] = 0;
		
		return matrix;
	}

	static public float[] concatenate(float[] A, 
			float B00, float B01, float B02, float B03,
			float B10, float B11, float B12, float B13,
			float B20, float B21, float B22, float B23,
			float B30, float B31, float B32, float B33
			) {
		
		final float A00 = A[ 0], A10 = A[ 1], A20 = A[ 2], A30 = A[ 3];
		final float A01 = A[ 4], A11 = A[ 5], A21 = A[ 6], A31 = A[ 7];
		final float A02 = A[ 8], A12 = A[ 9], A22 = A[10], A32 = A[11];
		final float A03 = A[12], A13 = A[13], A23 = A[14], A33 = A[15];
		
		A[ 0] = A03*B30+A02*B20+A01*B10+A00*B00;
		A[ 4] = A03*B31+A02*B21+A01*B11+A00*B01;
		A[ 8] = A03*B32+A02*B22+A01*B12+A00*B02;
		A[12] = A03*B33+A02*B23+A01*B13+A00*B03;
		
		A[ 1] = A13*B30+A12*B20+A11*B10+A10*B00;
		A[ 5] = A13*B31+A12*B21+A11*B11+A10*B01;
		A[ 9] = A13*B32+A12*B22+A11*B12+A10*B02;
		A[13] = A13*B33+A12*B23+A11*B13+A10*B03;
		
		A[ 2] = A23*B30+A22*B20+A21*B10+A20*B00;
		A[ 6] = A23*B31+A22*B21+A21*B11+A20*B01;
		A[10] = A23*B32+A22*B22+A21*B12+A20*B02;
		A[14] = A23*B33+A22*B23+A21*B13+A20*B03;
		
		A[ 3] = A33*B30+A32*B20+A31*B10+A30*B00;
		A[ 7] = A33*B31+A32*B21+A31*B11+A30*B01;
		A[11] = A33*B32+A32*B22+A31*B12+A30*B02;
		A[15] = A33*B33+A32*B23+A31*B13+A30*B03;
		
		return A;
	}
	
	static public float[] translate(float[] A, float tx, float ty, float tz) {
		return concatenate(A, 
				1, 0, 0, tx,
				0, 1, 0, ty,
				0, 0, 1, tz,
				0, 0, 0,  1				
			);
	}
	
	static public float[] rotate(float[] M, double theta, float ax, float ay, float az) {
		final float s = (float)sin(theta), c = (float)cos(theta), t = 1-c, l = (float)sqrt(ax*ax+ay*ay+az*az);
		final float x = (ax/l), y = (ay/l), z= (az/l);
		final float xz = x*z, xy = x*y, yz = y*z, xx=x*x, yy=y*y, zz=z*z;

		return concatenate(
			M, 
			t*xx+c  , t*xy-s*z, t*xz+s*y, 0,
			t*xy+s*z, t*yy+c  , t*yz-s*x, 0,
			t*xz-s*y, t*yz+s*x, t*zz+c  , 0,
			0,        0,      0,   1);
	}
	
	static public float[] scale(float[] M, float sx, float sy, float sz) {
		return concatenate(
			M, 
			sx,  0,  0,  0,
			 0, sy,  0,  0,
			 0,  0, sz,  0,
			 0,  0,  0,  1);
	}

	
	
	static public float[] ortho(float[] M, float left, float right, float bottom, float top, float near, float far) {
		float tx = (float) (- (right + left) / (right - left));
		float ty = (float) (- (top + bottom) / (top - bottom));
		float tz = (float) (- (far + near) / (far - near));

		return concatenate(
				M, 
			2/(right-left), 0,0, tx ,
			0, 2/(top-bottom), 0, ty,
			0, 0, -2/(far-near), tz,
			0,0,0,1
		);
	}
	
	
	static public float[] frustum(float[] M, float left, float right, float top, float bottom, float zNear, float zFar) {
		// see '$ man glFrustum'
		final float A = (right + left)/(right - left);
		final float B = (top + bottom)/(top - bottom);
		final float C = -(zFar + zNear)/(zFar - zNear);
		final float D = -(2 * zFar * zNear) /( zFar - zNear);

		return concatenate(
			M, 
			(2*zNear)/(right-left),                     0, A, 0,
			0                    , (2*zNear)/(top-bottom), B, 0,
			0                    ,                     0, C, D,
			0                    ,                     0,-1, 0
		);
	}
	
}



