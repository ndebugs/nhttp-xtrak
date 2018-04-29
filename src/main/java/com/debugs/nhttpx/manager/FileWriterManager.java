package com.debugs.nhttpx.manager;

import com.debugs.nhttpx.config.ApplicationProperties;
import com.debugs.nhttpx.io.FileWriterWrapper;
import com.debugs.nhttpx.io.WritableRow;
import com.debugs.nhttpx.message.Message;
import com.debugs.nhttpx.task.MessageTask;
import com.debugs.nhttpx.task.FileWriterTask;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.debugs.nhttpx.task.FileWriterTaskListener;

/**
 *
 * @author van de Bugs <van.de.bugs@gmail.com>
 */
@Component
public class FileWriterManager implements FileWriterTaskListener {
    
    @Autowired
    private ApplicationProperties applicationProperties;
    
    @Autowired
    private ExecutorManager executorManager;
    
    private final Logger LOGGER = LogManager.getLogger();
    private final Map<String, FileWriterWrapper> fileWriterMap = new HashMap<>();
    
    public File getFile(Message message) throws IOException {
        File dir = new File(applicationProperties.getOutputDir());
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File file = new File(dir, message.getName() + ".csv");
        if (!file.exists()) {
            file.createNewFile();
        }
        return file;
    }
    
    public OutputStreamWriter getWriter(FileWriterTask task) throws IOException {
        Message message = task.getMessage();
        String name = message.getName();
        
        FileWriterWrapper fileWriter = fileWriterMap.get(name);
        if (fileWriter == null) {
            File file = getFile(message);
            
            fileWriter = new FileWriterWrapper(file);
            fileWriter.open(true);
            
            fileWriterMap.put(name, fileWriter);
            
            LOGGER.info("Output stream for '{}' opened.", name);
        } else if (fileWriter.isClosed()) {
            fileWriter.open(false);
            
            LOGGER.info("Output stream for '{}' reopened.", name);
        }
        
        return fileWriter.getWriter();
    }
    
    public void write(WritableRow row, Message message, int position) {
        FileWriterTask task = new FileWriterTask(row, message);
        task.setPosition(position);
        
        try {
            OutputStreamWriter writer = getWriter(task);
            task.setWriter(writer);
            
            task.setListener(this);
            
            executorManager.execute(task);
        } catch (Exception e) {
            onError(task, e);
        }
    }
    
    public void flush(Message message) throws IOException {
        FileWriterWrapper fileWriter = fileWriterMap.get(message.getName());
        if (fileWriter != null && !fileWriter.isClosed()) {
            OutputStreamWriter writer = fileWriter.getWriter();
            writer.flush();
        }
    }

    public void close(Message message) throws IOException {
        executorManager.stopFileWriterService(message);
        
        FileWriterWrapper fileWriter = fileWriterMap.get(message.getName());
        if (fileWriter != null && !fileWriter.isClosed()) {
            OutputStreamWriter writer = fileWriter.getWriter();
            writer.flush();
            
            fileWriter.close();
            
            LOGGER.info("Output stream for '{}' closed.", message.getName());
        }
    }
    
    public void closeAll() throws IOException {
        executorManager.stopAllFileWriterService();
        
        for (FileWriterWrapper fileWriter : fileWriterMap.values()) {
            if (!fileWriter.isClosed()) {
                OutputStreamWriter writer = fileWriter.getWriter();
                writer.flush();
                
                fileWriter.close();
            }
        }
        
        LOGGER.info("All output stream closed.");
    }

    @Override
    public void onComplete(FileWriterTask task) {
        try {
            if (task.hasPosition(MessageTask.POSITION_LAST_SECTION)) {
                flush(task.getMessage());
            }
        } catch (IOException e) {
            onError(task, e);
        }
    }
    
    @Override
    public void onError(MessageTask task, Exception e) {
        LOGGER.catching(e);
    }
}
