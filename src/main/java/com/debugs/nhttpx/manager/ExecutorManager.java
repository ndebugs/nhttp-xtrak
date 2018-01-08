package com.debugs.nhttpx.manager;

import com.debugs.nhttpx.message.Message;
import com.debugs.nhttpx.task.FileWriterTask;
import com.debugs.nhttpx.task.MessageTask;
import com.debugs.nhttpx.task.RequestTask;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 * @author van de Bugs <van.de.bugs@gmail.com>
 */
@Component
public class ExecutorManager {
    
    @Autowired
    private RuntimeManager runtimeManager;
    
    private final Logger LOGGER = LogManager.getLogger();
    private final Map<String, ExecutorService> fileServiceMap = new HashMap<>();
    private final Map<ExecutorService, Future> futureMap = new HashMap<>();
    
    private ExecutorService connectionService;
    
    public ExecutorService getConnectionService() {
        if (connectionService == null) {
            connectionService = Executors.newSingleThreadExecutor();
            
            LOGGER.info("Connection service started.");
        }
        return connectionService;
    }
    
    public ExecutorService getFileWriterService(Message message) {
        String key = message.getName();
        
        ExecutorService service = fileServiceMap.get(key);
        if (service == null) {
            service = Executors.newSingleThreadExecutor();
            fileServiceMap.put(key, service);
            
            LOGGER.info("File Writer service for '{}' started.", key);
        }
        return service;
    }
    
    private void execute(ExecutorService service, MessageTask task) {
        synchronized (futureMap) {
            futureMap.put(service, service.submit(task));
        }
        
        runtimeManager.rescheduleTimeout();
    }
    
    public void execute(RequestTask task) {
        ExecutorService service = getConnectionService();
        execute(service, task);
    }
    
    public void execute(FileWriterTask task) {
        ExecutorService service = getFileWriterService(task.getMessage());
        execute(service, task);
    }
    
    private ExecutorStatus getStatus(ExecutorService service) {
        ExecutorStatus status = null;
        if (service != null) {
            synchronized (futureMap) {
                Future future = futureMap.get(service);
                if (future != null) {
                    status = !future.isDone() ? ExecutorStatus.ACTIVE : ExecutorStatus.IDLE;
                }
            }
        }
        return status != null ? status : ExecutorStatus.INACTIVE;
    }
    
    public ExecutorStatus getConnectionStatus() {
        return getStatus(connectionService);
    }
    
    public ExecutorStatus getFileWriterStatus(Message message) {
        String key = message.getName();
        
        ExecutorService service = fileServiceMap.get(key);
        return getStatus(service);
    }
    
    public void stopConnectionService() {
        if (connectionService != null) {
            connectionService.shutdownNow();
            connectionService = null;
            
            LOGGER.info("Connection service stopped.");
        }
    }
    
    public void stopFileWriterService(Message message) {
        String key = message.getName();
        
        ExecutorService service = fileServiceMap.remove(key);
        if (service != null) {
            service.shutdownNow();
            
            LOGGER.info("File Writer service for '{}' stopped.", key);
        }
    }
    
    public void stopAllFileWriterService() {
        for (ExecutorService service : fileServiceMap.values()) {
            service.shutdownNow();
        }
        fileServiceMap.clear();
        
        LOGGER.info("All File Writer service stopped.");
    }
}
