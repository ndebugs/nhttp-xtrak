package com.debugs.nhttpx.manager;

import com.debugs.nhttpx.config.ApplicationProperties;
import com.debugs.nhttpx.config.ConnectionProperties;
import com.debugs.nhttpx.evaluation.FieldEvaluator;
import com.debugs.nhttpx.io.WritableRow;
import com.debugs.nhttpx.message.Message;
import com.debugs.nhttpx.message.MessageSettings;
import com.debugs.nhttpx.message.DataWrapper;
import com.debugs.nhttpx.message.Response;
import com.debugs.nhttpx.task.MessageTask;
import com.debugs.nhttpx.task.RequestTask;
import com.debugs.nhttpx.task.RequestTaskListener;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.velocity.VelocityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 * @author van de Bugs <van.de.bugs@gmail.com>
 */
@Component
public class ProcessManager implements RequestTaskListener {

    private static final String CONTEXT_PARENT_DATA = "parent";
    private static final String CONTEXT_DATA = "data";
    
    @Autowired
    private ApplicationProperties applicationProperties;
    
    @Autowired
    private ConnectionProperties connectionProperties;
    
    @Autowired
    private MessageSettings messageSettings;
    
    @Autowired
    private ExecutorManager executorManager;
    
    @Autowired
    private FileWriterManager fileWriterManager;
    
    private final Logger LOGGER = LogManager.getLogger();
    
    public void doProcess() throws Exception {
        List<Message> messages = messageSettings.getMessages();
        
        if (messages != null && !messages.isEmpty()) {
            doProcess(0, null, 0);
        }
    }

    private void doProcess(int index, DataWrapper parent, int position) throws Exception {
        List<Message> messages = messageSettings.getMessages();
        
        Message message = messages.get(index);
        
        VelocityContext context = new VelocityContext();
        context.put(CONTEXT_PARENT_DATA, parent);
        
        RequestTask task = new RequestTask(context, message);
        task.setId(index);
        task.setPosition(position);
        task.setListener(this);
        task.setResponseCodePattern(connectionProperties.getResponseCodePattern());
        task.setMaxErrorRepeat(connectionProperties.getMaxErrorRepeat());
        task.setHasNext(index + 1 < messages.size());
        
        executorManager.execute(task);
    }
    
    private int makePosition(int index, int length) {
        int position = 0;
        if (index == 0) {
            position |= MessageTask.POSITION_FIRST_SECTION;
        }
        
        if (index == length - 1) {
            position |= MessageTask.POSITION_LAST_SECTION;
        }
        return position;
    }
    
    private void doFetch(RequestTask task, List datas) throws Exception {
        Message message = task.getMessage();
        Response response = message.getResponse();
        
        VelocityContext context = task.getContext();
        DataWrapper parentData = (DataWrapper) context.get(CONTEXT_PARENT_DATA);

        int nextIndex = task.getId() + 1;
        Set<WritableRow> rowSet = new HashSet<>();
        for (int i = 0; i < datas.size(); i++) {
            Object data = datas.get(i);

            VelocityContext subContext = new VelocityContext();
            subContext.put(CONTEXT_PARENT_DATA, parentData);
            subContext.put(CONTEXT_DATA, data);

            FieldEvaluator evaluator = new FieldEvaluator(subContext, message);
            String[] fields = evaluator.evaluate(response.getFields());
            
            WritableRow row = new WritableRow();
            row.setFields(fields);
            
            if (applicationProperties.isAllowDuplicate() || !rowSet.contains(row)) {
                LOGGER.debug("Data: {}", row);

                int position = makePosition(i, datas.size());
                int writerPosition = position;
                fileWriterManager.write(row, message, writerPosition);

                if (task.isHasNext()) {
                    DataWrapper nextParent = new DataWrapper(parentData, data);
                    doProcess(nextIndex, nextParent, position);
                }
                
                rowSet.add(row);
            } else {
                LOGGER.warn("Duplicate data: {}", row);
            }
        }
    }
    
    public void stop() throws IOException {
        executorManager.stopConnectionService();
        fileWriterManager.closeAll();
    }
    
    @Override
    public void onComplete(RequestTask task, Object data) {
        try {
            Message message = task.getMessage();
            Response response = message.getResponse();
            
            List subDatas = (List) (StringUtils.isNotBlank(response.getDataPath()) ?
                    PropertyUtils.getProperty(data, response.getDataPath()) : data);
            
            if (subDatas != null && !subDatas.isEmpty()) {
                doFetch(task, subDatas);
            } else {
                VelocityContext context = task.getContext();
                DataWrapper parentData = (DataWrapper) context.get(CONTEXT_PARENT_DATA);
                
                LOGGER.warn("No data from parent: {}", parentData);
            }
        } catch (Exception e) {
            onError(task, e);
        }
    }

    @Override
    public void onError(MessageTask task, Exception e) {
        LOGGER.catching(e);
    }
}
