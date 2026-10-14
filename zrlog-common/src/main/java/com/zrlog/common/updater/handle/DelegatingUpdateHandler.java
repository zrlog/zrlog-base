package com.zrlog.common.updater.handle;
import com.zrlog.common.updater.UpdateVersionHandler;
abstract class DelegatingUpdateHandler implements UpdateVersionHandler {
    protected final UpdateVersionHandler delegate;
    protected DelegatingUpdateHandler(UpdateVersionHandler delegate) { this.delegate = delegate; }
    public String getMessage() { return delegate.getMessage(); }
    public boolean isFinish() { return delegate.isFinish(); }
    public void doHandle() { delegate.doHandle(); }
}
