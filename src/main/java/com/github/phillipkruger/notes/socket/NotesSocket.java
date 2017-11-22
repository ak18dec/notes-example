package com.github.phillipkruger.notes.socket;

import com.github.phillipkruger.notes.Note;
import com.github.phillipkruger.notes.NoteNotFoundException;
import com.github.phillipkruger.notes.NotesService;
import com.github.phillipkruger.notes.event.ChangeEvent;
import java.io.IOException;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonWriter;
import javax.websocket.EncodeException;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import lombok.extern.java.Log;

/**
 * Web sockets 1.0, CDI 1.1
 * 
 * Endpoint for the websocket service
 * @author Phillip Kruger (phillip.kruger@phillip-kruger.com)
 */
@Log
@ServerEndpoint(value = "/note",encoders = {com.github.phillipkruger.notes.socket.JsonEncoder.class})
public class NotesSocket {
    
    @Inject 
    private NotesService notesService;
    
    public void noteChanged(@Observes ChangeEvent changeEvent){
        sendChangeEvent(changeEvent);
    }
    
    @OnOpen
    public void onOpen(Session session){
        sessions.add(session);
        log.log(Level.INFO, "Session joined [{0}]", session.getId());
        List<String> noteTitles = notesService.getNoteTitles();
        noteTitles.forEach((title) -> {
            try {
                sendNote(notesService.getNote(title));
            } catch (NoteNotFoundException ex) {
                log.log(Level.SEVERE, null, ex);
            }
        });
        
    }

    @OnClose
    public void onClose(Session session){
        sessions.remove(session);
        log.log(Level.INFO, "Session left [{0}]", session.getId());
    }
    
    @OnMessage
    public void onMessage(String message, Session session){
        if(message!=null && "".equalsIgnoreCase(message)){
            try {
                Note note = notesService.getNote(message);
                sendNote(note);
            } catch (NoteNotFoundException ex) {
                Logger.getLogger(NotesSocket.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
    
    private void sendNote(Note note){
        if(!sessions.isEmpty()){
            sessions.forEach((session) -> {
                try {
                    //session.getBasicRemote().sendObject(toJSON(note));// Does not work on OpenLiberty
                    session.getBasicRemote().sendText(toString(note));
                } catch (IOException  ex){//(IOException | EncodeException ex) {
                    log.log(Level.SEVERE, null, ex);
                }
            });
        }
    }
    
    private void sendChangeEvent(ChangeEvent changeEvent){
        if(!sessions.isEmpty()){
            sessions.forEach((session) -> {
                try {
                    //session.getBasicRemote().sendObject(toJSON(changeEvent)); // Does not work on OpenLiberty
                    session.getBasicRemote().sendText(toString(changeEvent));
                } catch (IOException  ex){//(IOException | EncodeException ex) {
                    log.log(Level.SEVERE, null, ex);
                }
            });
        }
    }
    
    private static final Queue<Session> sessions = new ConcurrentLinkedQueue<>();
    //private static final Set<Session> sessions = Collections.synchronizedSet(new HashSet<Session>());
    
    private JsonObject toJSON(ChangeEvent ce){
        JsonObjectBuilder job = Json.createObjectBuilder();
        Note note = ce.getNote();
        job.add("created", toJSONDate(note.getCreated()));
        job.add("lastUpdated", toJSONDate(note.getLastUpdated()));
        job.add("title", note.getTitle());
        job.add("text", note.getText());
        job.add("changeType", ce.getType());
        return job.build();
    }
    
    private JsonObject toJSON(Note note){
        JsonObjectBuilder job = Json.createObjectBuilder();
        
        job.add("created", toJSONDate(note.getCreated()));
        job.add("lastUpdated", toJSONDate(note.getLastUpdated()));
        job.add("title", note.getTitle());
        job.add("text", note.getText());
        return job.build();
    }
    
    
    private String toString(ChangeEvent ce) throws IOException{
        JsonObject jo = toJSON(ce);
        return toString(jo);
    }
    
    private String toString(Note note) throws IOException{
        JsonObject jo = toJSON(note);
        return toString(jo);
    }
    
    private String toString(JsonObject jo) throws IOException{
        try (StringWriter sw = new StringWriter(); 
                JsonWriter jw = Json.createWriter(sw)) {
            jw.writeObject(jo);
            return sw.toString();
        }
    }
    
    private String toJSONDate(Date date){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        return sdf.format(date);
    }
    
}