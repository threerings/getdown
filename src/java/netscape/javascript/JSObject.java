package netscape.javascript;

import java.applet.Applet;

/**
 * This just exists to compile against so that we don't have to link against plugin.jar
 */
public abstract class JSObject
{
    public abstract Object call (String func, Object[] args) throws JSException;
    public abstract Object eval (String code) throws JSException;
    public abstract Object getMember (String name) throws JSException;
    public abstract void setMember (String name, Object value) throws JSException;
    public abstract void removeMember (String name) throws JSException;
    public abstract Object getSlot (int slot) throws JSException;
    public abstract void setSlot (int slot, Object value) throws JSException;

    public static JSObject getWindow (Applet applet)
        throws JSException
    {
        return null;
    }
}
