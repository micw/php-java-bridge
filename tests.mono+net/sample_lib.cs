using System;
using System.Text;

namespace sample {
  public class ArrayToString {
    public static string Convert(int[] arr) {
	StringBuilder b = new StringBuilder(arr.Length);
	for(int i=0; i<arr.Length; i++) {
	    b.Append(arr[i]);
	    if(i+1<arr.Length) b.Append(" ");
	}
	return b.ToString();
    }
    public static string Convert(double[] arr) {
	StringBuilder b = new StringBuilder(arr.Length);
	for(int i=0; i<arr.Length; i++) {
	    b.Append(arr[i]);
	    if(i+1<arr.Length) b.Append(" ");
	}
	return b.ToString();
    }
    public static string Convert(bool[] arr) {
	StringBuilder b = new StringBuilder(arr.Length);
	for(int i=0; i<arr.Length; i++) {
	    b.Append(arr[i]);
	    if(i+1<arr.Length) b.Append(" ");
	}
	return b.ToString();
    }
  }	
}
