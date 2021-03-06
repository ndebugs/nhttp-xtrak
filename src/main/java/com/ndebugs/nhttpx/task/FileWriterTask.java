package com.ndebugs.nhttpx.task;

import com.ndebugs.nhttpx.io.WritableRow;
import com.ndebugs.nhttpx.message.Message;
import java.io.OutputStreamWriter;
import org.apache.commons.lang.StringEscapeUtils;

/**
 *
 * @author van de Bugs <van.de.bugs@gmail.com>
 */
public class FileWriterTask extends MessageTask {

    private WritableRow row;
    private OutputStreamWriter writer;
    private FileWriterTaskListener listener;

    public FileWriterTask(WritableRow row, Message message) {
        super(message);
        
        this.row = row;
    }

    public WritableRow getRow() {
        return row;
    }

    public void setRow(WritableRow row) {
        this.row = row;
    }

    public OutputStreamWriter getWriter() {
        return writer;
    }

    public void setWriter(OutputStreamWriter writer) {
        this.writer = writer;
    }

    public FileWriterTaskListener getListener() {
        return listener;
    }

    public void setListener(FileWriterTaskListener listener) {
        this.listener = listener;
    }

    @Override
    public void run() {
        try {
            String[] fields = row.getFields();
            for (int i = 0; i < fields.length; i++) {
                String field = fields[i];
                
                if (i > 0) {
                    writer.write(',');
                }
                StringEscapeUtils.escapeCsv(writer, field);
            }
            writer.append('\n');
            
            listener.onComplete(this);
        } catch (Exception e) {
            listener.onError(this, e);
        }
    }
}
