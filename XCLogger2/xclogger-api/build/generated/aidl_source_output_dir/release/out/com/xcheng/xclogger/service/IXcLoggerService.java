/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.xcheng.xclogger.service;
public interface IXcLoggerService extends android.os.IInterface
{
  /** Default implementation for IXcLoggerService. */
  public static class Default implements com.xcheng.xclogger.service.IXcLoggerService
  {
    // 业务控制
    @Override public boolean startLogging() throws android.os.RemoteException
    {
      return false;
    }
    @Override public boolean stopLogging() throws android.os.RemoteException
    {
      return false;
    }
    @Override public boolean isRunning() throws android.os.RemoteException
    {
      return false;
    }
    // 配置控制（部分更新语义：未设置字段保持现有值）
    @Override public com.xcheng.xclogger.util.XcLoggerConfig getConfiguration() throws android.os.RemoteException
    {
      return null;
    }
    @Override public boolean updateConfigurationPartial(com.xcheng.xclogger.util.XcLoggerConfig config) throws android.os.RemoteException
    {
      return false;
    }
    // 触发按天压缩并导出到 /data/xclogger/mobilelog
    @Override public boolean triggerCompression() throws android.os.RemoteException
    {
      return false;
    }
    @Override public boolean reportUploadResult(boolean success) throws android.os.RemoteException
    {
      return false;
    }
    @Override public java.lang.String getCompressStatus() throws android.os.RemoteException
    {
      return null;
    }
    @Override public boolean cancelCompressTask() throws android.os.RemoteException
    {
      return false;
    }
    // 监听器注册
    @Override public void registerListener(com.xcheng.xclogger.service.IXcLoggerListener listener) throws android.os.RemoteException
    {
    }
    @Override public void unregisterListener(com.xcheng.xclogger.service.IXcLoggerListener listener) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.xcheng.xclogger.service.IXcLoggerService
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.xcheng.xclogger.service.IXcLoggerService interface,
     * generating a proxy if needed.
     */
    public static com.xcheng.xclogger.service.IXcLoggerService asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.xcheng.xclogger.service.IXcLoggerService))) {
        return ((com.xcheng.xclogger.service.IXcLoggerService)iin);
      }
      return new com.xcheng.xclogger.service.IXcLoggerService.Stub.Proxy(obj);
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
        case TRANSACTION_startLogging:
        {
          boolean _result = this.startLogging();
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_stopLogging:
        {
          boolean _result = this.stopLogging();
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_isRunning:
        {
          boolean _result = this.isRunning();
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_getConfiguration:
        {
          com.xcheng.xclogger.util.XcLoggerConfig _result = this.getConfiguration();
          reply.writeNoException();
          _Parcel.writeTypedObject(reply, _result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_updateConfigurationPartial:
        {
          com.xcheng.xclogger.util.XcLoggerConfig _arg0;
          _arg0 = _Parcel.readTypedObject(data, com.xcheng.xclogger.util.XcLoggerConfig.CREATOR);
          boolean _result = this.updateConfigurationPartial(_arg0);
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_triggerCompression:
        {
          boolean _result = this.triggerCompression();
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_reportUploadResult:
        {
          boolean _arg0;
          _arg0 = (0!=data.readInt());
          boolean _result = this.reportUploadResult(_arg0);
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_getCompressStatus:
        {
          java.lang.String _result = this.getCompressStatus();
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_cancelCompressTask:
        {
          boolean _result = this.cancelCompressTask();
          reply.writeNoException();
          reply.writeInt(((_result)?(1):(0)));
          break;
        }
        case TRANSACTION_registerListener:
        {
          com.xcheng.xclogger.service.IXcLoggerListener _arg0;
          _arg0 = com.xcheng.xclogger.service.IXcLoggerListener.Stub.asInterface(data.readStrongBinder());
          this.registerListener(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_unregisterListener:
        {
          com.xcheng.xclogger.service.IXcLoggerListener _arg0;
          _arg0 = com.xcheng.xclogger.service.IXcLoggerListener.Stub.asInterface(data.readStrongBinder());
          this.unregisterListener(_arg0);
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
    private static class Proxy implements com.xcheng.xclogger.service.IXcLoggerService
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
      // 业务控制
      @Override public boolean startLogging() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_startLogging, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public boolean stopLogging() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_stopLogging, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public boolean isRunning() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_isRunning, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      // 配置控制（部分更新语义：未设置字段保持现有值）
      @Override public com.xcheng.xclogger.util.XcLoggerConfig getConfiguration() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        com.xcheng.xclogger.util.XcLoggerConfig _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getConfiguration, _data, _reply, 0);
          _reply.readException();
          _result = _Parcel.readTypedObject(_reply, com.xcheng.xclogger.util.XcLoggerConfig.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public boolean updateConfigurationPartial(com.xcheng.xclogger.util.XcLoggerConfig config) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _Parcel.writeTypedObject(_data, config, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_updateConfigurationPartial, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      // 触发按天压缩并导出到 /data/xclogger/mobilelog
      @Override public boolean triggerCompression() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_triggerCompression, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public boolean reportUploadResult(boolean success) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(((success)?(1):(0)));
          boolean _status = mRemote.transact(Stub.TRANSACTION_reportUploadResult, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String getCompressStatus() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getCompressStatus, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public boolean cancelCompressTask() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_cancelCompressTask, _data, _reply, 0);
          _reply.readException();
          _result = (0!=_reply.readInt());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      // 监听器注册
      @Override public void registerListener(com.xcheng.xclogger.service.IXcLoggerListener listener) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(listener);
          boolean _status = mRemote.transact(Stub.TRANSACTION_registerListener, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void unregisterListener(com.xcheng.xclogger.service.IXcLoggerListener listener) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(listener);
          boolean _status = mRemote.transact(Stub.TRANSACTION_unregisterListener, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_startLogging = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_stopLogging = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_isRunning = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_getConfiguration = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_updateConfigurationPartial = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
    static final int TRANSACTION_triggerCompression = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
    static final int TRANSACTION_reportUploadResult = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
    static final int TRANSACTION_getCompressStatus = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
    static final int TRANSACTION_cancelCompressTask = (android.os.IBinder.FIRST_CALL_TRANSACTION + 8);
    static final int TRANSACTION_registerListener = (android.os.IBinder.FIRST_CALL_TRANSACTION + 9);
    static final int TRANSACTION_unregisterListener = (android.os.IBinder.FIRST_CALL_TRANSACTION + 10);
  }
  public static final java.lang.String DESCRIPTOR = "com.xcheng.xclogger.service.IXcLoggerService";
  // 业务控制
  public boolean startLogging() throws android.os.RemoteException;
  public boolean stopLogging() throws android.os.RemoteException;
  public boolean isRunning() throws android.os.RemoteException;
  // 配置控制（部分更新语义：未设置字段保持现有值）
  public com.xcheng.xclogger.util.XcLoggerConfig getConfiguration() throws android.os.RemoteException;
  public boolean updateConfigurationPartial(com.xcheng.xclogger.util.XcLoggerConfig config) throws android.os.RemoteException;
  // 触发按天压缩并导出到 /data/xclogger/mobilelog
  public boolean triggerCompression() throws android.os.RemoteException;
  public boolean reportUploadResult(boolean success) throws android.os.RemoteException;
  public java.lang.String getCompressStatus() throws android.os.RemoteException;
  public boolean cancelCompressTask() throws android.os.RemoteException;
  // 监听器注册
  public void registerListener(com.xcheng.xclogger.service.IXcLoggerListener listener) throws android.os.RemoteException;
  public void unregisterListener(com.xcheng.xclogger.service.IXcLoggerListener listener) throws android.os.RemoteException;
  /** @hide */
  static class _Parcel {
    static private <T> T readTypedObject(
        android.os.Parcel parcel,
        android.os.Parcelable.Creator<T> c) {
      if (parcel.readInt() != 0) {
          return c.createFromParcel(parcel);
      } else {
          return null;
      }
    }
    static private <T extends android.os.Parcelable> void writeTypedObject(
        android.os.Parcel parcel, T value, int parcelableFlags) {
      if (value != null) {
        parcel.writeInt(1);
        value.writeToParcel(parcel, parcelableFlags);
      } else {
        parcel.writeInt(0);
      }
    }
  }
}
