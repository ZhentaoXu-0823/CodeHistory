/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.xcheng.xclogger.service;
public interface IXcLoggerListener extends android.os.IInterface
{
  /** Default implementation for IXcLoggerListener. */
  public static class Default implements com.xcheng.xclogger.service.IXcLoggerListener
  {
    // 日志状态变化通知 (0: Stopped, 1: Running)
    @Override public void onStatusChanged(int status) throws android.os.RemoteException
    {
    }
    // 统一操作结果回调
    @Override public void onOperationResult(java.lang.String opType, boolean success, java.lang.String message, boolean runningState) throws android.os.RemoteException
    {
    }
    // 压缩任务完成通知
    @Override public void onCompressFinished(boolean success, java.lang.String message) throws android.os.RemoteException
    {
    }
    // 压缩包已准备好，等待外部上传
    @Override public void onCompressReady(java.lang.String zipFiles, int retryCount, int maxRetryCount) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.xcheng.xclogger.service.IXcLoggerListener
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.xcheng.xclogger.service.IXcLoggerListener interface,
     * generating a proxy if needed.
     */
    public static com.xcheng.xclogger.service.IXcLoggerListener asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.xcheng.xclogger.service.IXcLoggerListener))) {
        return ((com.xcheng.xclogger.service.IXcLoggerListener)iin);
      }
      return new com.xcheng.xclogger.service.IXcLoggerListener.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      switch (code)
      {
        case INTERFACE_TRANSACTION:
        {
          reply.writeString(descriptor);
          return true;
        }
      }
      switch (code)
      {
        case TRANSACTION_onStatusChanged:
        {
          int _arg0;
          _arg0 = data.readInt();
          this.onStatusChanged(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_onOperationResult:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          boolean _arg1;
          _arg1 = (0!=data.readInt());
          java.lang.String _arg2;
          _arg2 = data.readString();
          boolean _arg3;
          _arg3 = (0!=data.readInt());
          this.onOperationResult(_arg0, _arg1, _arg2, _arg3);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_onCompressFinished:
        {
          boolean _arg0;
          _arg0 = (0!=data.readInt());
          java.lang.String _arg1;
          _arg1 = data.readString();
          this.onCompressFinished(_arg0, _arg1);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_onCompressReady:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          int _arg1;
          _arg1 = data.readInt();
          int _arg2;
          _arg2 = data.readInt();
          this.onCompressReady(_arg0, _arg1, _arg2);
          reply.writeNoException();
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.xcheng.xclogger.service.IXcLoggerListener
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      // 日志状态变化通知 (0: Stopped, 1: Running)
      @Override public void onStatusChanged(int status) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(status);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onStatusChanged, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      // 统一操作结果回调
      @Override public void onOperationResult(java.lang.String opType, boolean success, java.lang.String message, boolean runningState) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(opType);
          _data.writeInt(((success)?(1):(0)));
          _data.writeString(message);
          _data.writeInt(((runningState)?(1):(0)));
          boolean _status = mRemote.transact(Stub.TRANSACTION_onOperationResult, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      // 压缩任务完成通知
      @Override public void onCompressFinished(boolean success, java.lang.String message) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(((success)?(1):(0)));
          _data.writeString(message);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onCompressFinished, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      // 压缩包已准备好，等待外部上传
      @Override public void onCompressReady(java.lang.String zipFiles, int retryCount, int maxRetryCount) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(zipFiles);
          _data.writeInt(retryCount);
          _data.writeInt(maxRetryCount);
          boolean _status = mRemote.transact(Stub.TRANSACTION_onCompressReady, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_onStatusChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_onOperationResult = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_onCompressFinished = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_onCompressReady = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
  }
  public static final java.lang.String DESCRIPTOR = "com.xcheng.xclogger.service.IXcLoggerListener";
  // 日志状态变化通知 (0: Stopped, 1: Running)
  public void onStatusChanged(int status) throws android.os.RemoteException;
  // 统一操作结果回调
  public void onOperationResult(java.lang.String opType, boolean success, java.lang.String message, boolean runningState) throws android.os.RemoteException;
  // 压缩任务完成通知
  public void onCompressFinished(boolean success, java.lang.String message) throws android.os.RemoteException;
  // 压缩包已准备好，等待外部上传
  public void onCompressReady(java.lang.String zipFiles, int retryCount, int maxRetryCount) throws android.os.RemoteException;
}
