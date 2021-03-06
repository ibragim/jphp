package org.develnext.jphp.swing.classes.components.support;

import org.develnext.jphp.swing.ComponentProperties;
import org.develnext.jphp.swing.SwingExtension;
import org.develnext.jphp.swing.XYLayout;
import org.develnext.jphp.swing.classes.WrapBorder;
import org.develnext.jphp.swing.classes.WrapColor;
import org.develnext.jphp.swing.classes.WrapFont;
import org.develnext.jphp.swing.classes.WrapGraphics;
import org.develnext.jphp.swing.classes.components.UIPopupMenu;
import org.develnext.jphp.swing.classes.components.UIUnknown;
import org.develnext.jphp.swing.event.EventProvider;
import org.develnext.jphp.swing.misc.Align;
import org.develnext.jphp.swing.misc.Anchor;
import org.develnext.jphp.swing.misc.EventContainer;
import php.runtime.Memory;
import php.runtime.common.HintType;
import php.runtime.env.Environment;
import php.runtime.invoke.Invoker;
import php.runtime.lang.ForeachIterator;
import php.runtime.memory.ArrayMemory;
import php.runtime.memory.LongMemory;
import php.runtime.memory.ObjectMemory;
import php.runtime.memory.StringMemory;
import php.runtime.reflection.ClassEntity;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import static php.runtime.annotation.Reflection.*;

@Name(SwingExtension.NAMESPACE + "UIElement")
abstract public class UIElement extends RootObject {
    protected EventContainer cacheEventContainer;
    protected Set<String> allowedEvents;

    public UIElement(Environment env) {
        super(env);
    }

    public UIElement(Environment env, ClassEntity clazz) {
        super(env, clazz);
    }

    abstract public Component getComponent();

    public JComponent getJComponent() {
        Component component = getComponent();
        if (component instanceof JComponent)
            return (JComponent) component;

        throw new IllegalArgumentException("Unsupported operation");
    }

    public Component getContentComponent() {
        return getComponent();
    }

    abstract public void setComponent(Component component);

    abstract protected void onInit(Environment env, Memory... args);

    protected void onAfterInit(Environment env, Memory... args) {
        SwingExtension.registerComponent(getComponent());
    }

    protected EventContainer getEventContainer() {
        if (cacheEventContainer != null)
            return cacheEventContainer;

        ComponentProperties properties = SwingExtension.getProperties(getComponent());
        return properties == null ? null : (cacheEventContainer = properties.eventContainer);
    }

    @Signature
    public Memory __construct(Environment env, Memory... args) {
        onInit(env, args);
        onAfterInit(env, args);
        return Memory.NULL;
    }

    protected void onBindEvent(final Environment env, String name, Invoker invoker) {
    }

    @Signature
    public Memory __debugInfo(Environment env, Memory... args) {
        ArrayMemory result = new ArrayMemory();
        result.refOfIndex("uid").assign(getComponent().getName());

        result.refOfIndex("position").assign(ArrayMemory.ofIntegers(getComponent().getX(), getComponent().getY()));
        result.refOfIndex("size").assign(ArrayMemory.ofIntegers(getComponent().getWidth(), getComponent().getHeight()));

        ComponentProperties properties = SwingExtension.getProperties(getComponent());
        if (properties.getOriginGroups() != null)
            result.refOfIndex("group").assign(properties.getOriginGroups());

        return result.toConstant();
    }

    @Signature({@Arg("name"), @Arg("callback"), @Arg(value = "group", optional = @Optional("general"))})
    public Memory on(Environment env, Memory... args) {
        Invoker invoker = Invoker.valueOf(env, null, args[1]);
        if (invoker == null) {
            env.exception(env.trace(), "Argument 2 must be callable");
            return Memory.NULL;
        }

        String name = args[0].toString().toLowerCase();
        EventProvider eventProvider = SwingExtension.isAllowedEventType(getComponent(), name);
        invoker.setTrace(env.trace());

        if (eventProvider == null) {
            if (allowedEvents == null || !allowedEvents.contains(name))
                throw new IllegalArgumentException("Unknown event type - " + args[0]);
            //env.exception(env.trace(), "Unknown event type - " + args[0]);
        }

        onBindEvent(env, name, invoker);
        ComponentProperties properties = SwingExtension.getProperties(getComponent(), true);
        properties.updateEvents(env);

        getEventContainer().addEvent(name, args[2].toString(), invoker);
        return Memory.NULL;
    }

    @Signature({@Arg("name"), @Arg(value = "group", optional = @Optional("NULL"))})
    public Memory off(Environment env, Memory... args) {
        if (args[1].isNull()) {
            return getEventContainer().clearEvent(args[0].toString()) == null ? Memory.FALSE : Memory.TRUE;
        } else {
            return getEventContainer().clearEvent(args[0].toString(), args[1].toString()) == null
                    ? Memory.FALSE : Memory.TRUE;
        }
    }

    @Signature({@Arg("name")})
    public Memory trigger(Environment env, Memory... args) throws Throwable {
        ComponentProperties properties = SwingExtension.getProperties(getComponent());
        if (args.length == 1) {
            properties.triggerEvent(args[0].toString().toLowerCase());
        } else {
            Memory[] passed = new Memory[args.length - 1];
            System.arraycopy(args, 1, passed, 0, args.length - 1);
            properties.triggerEvent(args[0].toString().toLowerCase(), passed);
        }
        return Memory.NULL;
    }

    @Signature({
            @Arg("name")
    })
    protected Memory addAllowedEventType(Environment env, Memory... args) {
        if (allowedEvents == null)
            allowedEvents = new HashSet<String>();

        allowedEvents.add(args[0].toString().toLowerCase());
        return Memory.NULL;
    }


    @Signature(@Arg("value"))
    protected Memory __setAutosize(Environment env, Memory... args) {
        ComponentProperties properties = SwingExtension.getProperties(getComponent());
        properties.setAutoSize(args[0].toBoolean());
        return Memory.NULL;
    }

    @Signature
    protected Memory __getAutosize(Environment env, Memory... args) {
        ComponentProperties properties = SwingExtension.getProperties(getComponent());
        return properties.isAutoSize() ? Memory.TRUE : Memory.FALSE;
    }

    @Signature
    protected Memory __getAlign(Environment env, Memory... args) {
        ComponentProperties properties = SwingExtension.getProperties(getComponent());

        if (properties != null)
            return new StringMemory(properties.getAlign().name());

        return Memory.NULL;
    }

    @Signature(@Arg("value"))
    protected Memory __setAlign(Environment env, Memory... args) {
        ComponentProperties properties = SwingExtension.getProperties(getComponent());
        properties.setAlign(Align.valueOf(args[0].toString().toUpperCase()));
        return Memory.NULL;
    }

    @Signature
    protected Memory __getAnchors(Environment env, Memory... args) {
        ArrayMemory result = new ArrayMemory();
        ComponentProperties properties = SwingExtension.getProperties(getComponent());

        if (properties != null)
            for (Anchor anchor : properties.anchors) {
                result.add(new StringMemory(anchor.name()));
            }

        return result.toConstant();
    }

    @Signature(@Arg("value"))
    protected Memory __setAnchors(Environment env, Memory... args) {
        ComponentProperties data = SwingExtension.getProperties(getComponent());
        data.anchors.clear();

        if (args[0].isArray()) {
            ForeachIterator iterator = args[0].getNewIterator(env, false, false);
            while (iterator.next()) {
                Anchor anchor = Anchor.valueOf(iterator.getValue().toString().toUpperCase());
                if (anchor == null)
                    env.exception(env.trace(), "Invalid anchor value - " + iterator.getValue());
                data.anchors.add(anchor);
            }
        } else {
            Anchor anchor = Anchor.valueOf(args[0].toString().toUpperCase());
            if (anchor == null)
                env.exception(env.trace(), "Invalid anchor value - " + args[0]);
        }

        if (getComponent().getParent() != null) {
            LayoutManager layout = getComponent().getParent().getLayout();
            if (!(layout instanceof XYLayout))
                env.exception(env.trace(), "Layout must be an instance of XYLayout");
        }
        return Memory.NULL;
    }

    @Signature(@Arg(value = "value", type = HintType.ARRAY))
    protected Memory __setPadding(Environment env, Memory... args) {
        ComponentProperties properties = SwingExtension.getProperties(getComponent());
        if (properties == null)
            return Memory.NULL;

        int[] v = args[0].toValue(ArrayMemory.class).toIntArray();
        if (v.length == 1) {
            int size = v[0];
            properties.setPadding(size, size, size, size);
        } else if (v.length == 2) {
            int ver = v[0];
            int hor = v[1];
            properties.setPadding(ver, hor, ver, hor);
        } else if (v.length == 3) {
            int top = v[0];
            int hor = v[1];
            int bottom = v[2];
            properties.setPadding(top, hor, bottom, hor);
        } else if (v.length > 3) {
            int top = v[0];
            int right = v[1];
            int bottom = v[2];
            int left = v[3];
            properties.setPadding(top, right, bottom, left);
        }

        return Memory.NULL;
    }

    @Signature
    protected Memory __getPadding(Environment env, Memory... args) {
        ComponentProperties properties = SwingExtension.getProperties(getComponent());
        if (properties == null)
            return Memory.NULL;

        return ArrayMemory.ofIntegers(properties.getPadding()).toConstant();
    }

    @Signature
    protected Memory __getSize(Environment env, Memory... args) {
        return ArrayMemory.ofIntegers(getComponent().getWidth(), getComponent().getHeight()).toConstant();
    }

    @Signature({@Arg(value = "size", type = HintType.ARRAY)})
    protected Memory __setSize(Environment env, Memory... args) {
        Memory[] size = args[0].toValue(ArrayMemory.class).values(false);
        if (size.length >= 2) {
            getComponent().setSize(size[0].toInteger(), size[1].toInteger());
        }
        return Memory.NULL;
    }

    @Signature
    protected Memory __getPreferredSize(Environment env, Memory... args) {
        Dimension dimension = getComponent().getPreferredSize();
        return ArrayMemory.ofIntegers((int) dimension.getWidth(), (int) dimension.getHeight()).toConstant();
    }

    @Signature({@Arg(value = "size", type = HintType.ARRAY)})
    protected Memory __setPreferredSize(Environment env, Memory... args) {
        Memory[] size = args[0].toValue(ArrayMemory.class).values(false);
        if (size.length >= 2) {
            getComponent().setPreferredSize(new Dimension(size[0].toInteger(), size[1].toInteger()));
        }
        return Memory.NULL;
    }

    @Signature
    protected Memory __getMinSize(Environment env, Memory... args) {
        Dimension dimension = getComponent().getMinimumSize();
        return ArrayMemory.ofDoubles(dimension.getWidth(), dimension.getHeight());
    }

    @Signature({@Arg(value = "size", type = HintType.ARRAY)})
    protected Memory __setMinSize(Environment env, Memory... args) {
        Memory[] size = args[0].toValue(ArrayMemory.class).values(false);
        if (size.length >= 2) {
            getComponent().setMinimumSize(new Dimension(
                    size[0].toInteger(), size[1].toInteger()
            ));
        }
        return Memory.NULL;
    }

    @Signature
    protected Memory __getPosition(Environment env, Memory... args) {
        return ArrayMemory.ofIntegers(getComponent().getX(), getComponent().getY()).toConstant();
    }

    @Signature
    protected Memory __getScreenPosition(Environment env, Memory... args) {
        Point pt = getComponent().getLocationOnScreen();
        return ArrayMemory.ofIntegers(pt.x, pt.y).toConstant();
    }

    @Signature
    protected Memory __getParent(Environment env, Memory... args) {
        if (getComponent().getParent() != null) {
            return ObjectMemory.valueOf(UIElement.of(env, getComponent().getParent()));
        }

        return null;
    }

    @Signature
    protected Memory __getFirstParent(Environment env, Memory... args) {
        Container parent = getComponent().getParent();

        while (parent != null) {
            if (parent.getParent() == null) break;

            parent = parent.getParent();
        }

        return parent == null ? Memory.NULL : ObjectMemory.valueOf(UIElement.of(env, parent));
    }

    @Signature
    protected Memory __getAbsolutePosition(Environment env, Memory... args) {
        int x = getComponent().getX();
        int y = getComponent().getY();

        Container parent = getComponent().getParent();

        while (parent != null) {
            if (parent instanceof Window) break;

            x += parent.getX();
            y += parent.getY();

            parent = parent.getParent();
        }

        return ArrayMemory.ofIntegers(x, y).toConstant();
    }

    @Signature({@Arg(value = "position", type = HintType.ARRAY)})
    protected Memory __setScreenPosition(Environment env, Memory... args) {
        Memory[] size = args[0].toValue(ArrayMemory.class).values(false);
        if (size.length >= 2) {
            Point pt = new Point(size[0].toInteger(), size[1].toInteger());

            SwingUtilities.convertPointFromScreen(pt, getComponent());

            getComponent().setLocation(pt.x, pt.y);
        }

        return Memory.NULL;
    }

    @Signature({@Arg(value = "position", type = HintType.ARRAY)})
    public Memory __setPosition(Environment env, Memory... args) {
        Memory[] size = args[0].toValue(ArrayMemory.class).values(false);
        if (size.length >= 2)
            getComponent().setLocation(size[0].toInteger(), size[1].toInteger());
        return Memory.NULL;
    }

    @Signature
    protected Memory __getW(Environment env, Memory... args) {
        return new LongMemory(getComponent().getWidth());
    }

    @Signature(@Arg("value"))
    protected Memory __setW(Environment env, Memory... args) {
        getComponent().setSize(args[0].toInteger(), getComponent().getHeight());
        return Memory.NULL;
    }

    @Signature
    protected Memory __getH(Environment env, Memory... args) {
        return new LongMemory(getComponent().getHeight());
    }

    @Signature(@Arg("value"))
    protected Memory __setH(Environment env, Memory... args) {
        getComponent().setSize(getComponent().getWidth(), args[0].toInteger());
        return Memory.NULL;
    }

    @Signature
    protected Memory __getX(Environment env, Memory... args) {
        return LongMemory.valueOf(getComponent().getX());
    }

    @Signature(@Arg("value"))
    protected Memory __setX(Environment env, Memory... args) {
        getComponent().setLocation(args[0].toInteger(), getComponent().getY());
        return Memory.NULL;
    }

    @Signature
    protected Memory __getY(Environment env, Memory... args) {
        return LongMemory.valueOf(getComponent().getY());
    }

    @Signature(@Arg("value"))
    protected Memory __setY(Environment env, Memory... args) {
        getComponent().setLocation(getComponent().getX(), args[0].toInteger());
        return Memory.NULL;
    }

    @Signature
    protected Memory __getVisible(Environment env, Memory... args) {
        return getComponent().isVisible() ? Memory.TRUE : Memory.FALSE;
    }

    @Signature(@Arg("value"))
    protected Memory __setVisible(Environment env, Memory... args) {
        getComponent().setVisible(args[0].toBoolean());
        return Memory.NULL;
    }

    @Signature
    protected Memory __getEnabled(Environment env, Memory... args) {
        return getComponent().isEnabled() ? Memory.TRUE : Memory.FALSE;
    }

    @Signature(@Arg("value"))
    protected Memory __setEnabled(Environment env, Memory... args) {
        getComponent().setEnabled(args[0].toBoolean());
        return Memory.NULL;
    }

    @Signature
    protected Memory __getFocusable(Environment env, Memory... args) {
        return getComponent().isFocusable() ? Memory.TRUE : Memory.FALSE;
    }

    @Signature(@Arg("value"))
    protected Memory __setFocusable(Environment env, Memory... args) {
        getComponent().setFocusable(args[0].toBoolean());
        return Memory.NULL;
    }

    @Signature
    protected Memory __getUid(Environment env, Memory... args) {
        return StringMemory.valueOf(getComponent().getName());
    }

    @Signature
    protected Memory __getGroup(Environment env, Memory... args) {
        ComponentProperties properties = SwingExtension.getProperties(getComponent());
        return new StringMemory(properties.getOriginGroups());
    }

    @Signature(@Arg("value"))
    protected Memory __setGroup(Environment env, Memory... args) {
        ComponentProperties properties = SwingExtension.getProperties(getComponent());
        properties.setGroups(args[0].toString());
        return Memory.NULL;
    }

    @Signature
    protected Memory __getFont(Environment env, Memory... args) {
        return new ObjectMemory(new WrapFont(env, getComponent().getFont()));
    }

    @Signature(@Arg("value"))
    protected Memory __setFont(Environment env, Memory... args) {
        getComponent().setFont(WrapFont.of(args[0]));
        return Memory.NULL;
    }

    @Signature
    protected Memory __getBorder(Environment env, Memory... args) {
        if (getJComponent().getBorder() == null)
            return Memory.NULL;
        return new ObjectMemory(new WrapBorder(env, getJComponent().getBorder()));
    }

    @Signature(@Arg(value = "border", typeClass = SwingExtension.NAMESPACE + "Border", optional = @Optional("NULL")))
    protected Memory __setBorder(Environment env, Memory... args) {
        getJComponent().setBorder(args[0].isNull() ? null : args[0].toObject(WrapBorder.class).getBorder());
        return Memory.NULL;
    }

    @Signature
    protected Memory __getBackground(Environment env, Memory... args) {
        return new ObjectMemory(new WrapColor(env, getContentComponent().getBackground()));
    }

    @Signature(@Arg("color"))
    protected Memory __setBackground(Environment env, Memory... args) {
        getContentComponent().setBackground(WrapColor.of(args[0]));
        return Memory.NULL;
    }

    @Signature
    protected Memory __getForeground(Environment env, Memory... args) {
        return new ObjectMemory(new WrapColor(env, getContentComponent().getForeground()));
    }

    @Signature(@Arg("color"))
    protected Memory __setForeground(Environment env, Memory... args) {
        getContentComponent().setForeground(WrapColor.of(args[0]));
        return Memory.NULL;
    }

    @Signature
    public Memory getGraphics(Environment env, Memory... args) {
        Graphics graphics = getContentComponent().getGraphics();
        if (graphics == null)
            return Memory.NULL;

        return new ObjectMemory(new WrapGraphics(env, graphics));
    }

    @Signature
    public Memory updateUI(Environment env, Memory... args) {
        getJComponent().updateUI();
        return Memory.NULL;
    }

    @Signature
    protected Memory __getTooltipText(Environment env, Memory... args) {
        return new StringMemory(getJComponent().getToolTipText());
    }

    @Signature(@Arg("value"))
    protected Memory __setTooltipText(Environment env, Memory... args) {
        getJComponent().setToolTipText(args[0].toString());
        return Memory.NULL;
    }

    @Signature
    protected Memory __getDoubleBuffered(Environment env, Memory... args) {
        return getJComponent().isDoubleBuffered() ? Memory.TRUE : Memory.FALSE;
    }

    @Signature(@Arg("value"))
    protected Memory __setDoubleBuffered(Environment env, Memory... args) {
        getJComponent().setDoubleBuffered(args[0].toBoolean());
        return Memory.NULL;
    }

    @Signature
    protected Memory __getOpaque(Environment env, Memory... args) {
        return getJComponent().isOpaque() ? Memory.TRUE : Memory.FALSE;
    }

    @Signature(@Arg("value"))
    protected Memory __setOpaque(Environment env, Memory... args) {
        getJComponent().setOpaque(args[0].toBoolean());
        return Memory.NULL;
    }

    @Signature
    protected Memory __getAutoscrolls(Environment env, Memory... args) {
        return getJComponent().getAutoscrolls() ? Memory.TRUE : Memory.FALSE;
    }

    @Signature(@Arg("value"))
    protected Memory __setAutoscrolls(Environment env, Memory... args) {
        getJComponent().setAutoscrolls(args[0].toBoolean());
        return Memory.NULL;
    }

    @Signature
    protected Memory __getIgnoreRepaint(Environment env, Memory... args) {
        return getComponent().getIgnoreRepaint() ? Memory.TRUE : Memory.FALSE;
    }

    @Signature(@Arg("value"))
    protected Memory __setIgnoreRepaint(Environment env, Memory... args) {
        getComponent().setIgnoreRepaint(args[0].toBoolean());
        return Memory.NULL;
    }

    @Signature
    protected Memory __getUIClassId(Environment env, Memory... args) {
        return new StringMemory(getJComponent().getUIClassID());
    }

    @Signature
    public Memory invalidate(Environment env, Memory... args) {
        getComponent().invalidate();
        return Memory.NULL;
    }

    @Signature(@Arg(value = "canvas", typeClass = SwingExtension.NAMESPACE + "Graphics"))
    public Memory printOne(Environment env, Memory... args) {
        getComponent().print(args[0].toObject(WrapGraphics.class).getGraphics());
        return Memory.NULL;
    }

    @Signature(@Arg(value = "canvas", typeClass = SwingExtension.NAMESPACE + "Graphics"))
    public Memory printAll(Environment env, Memory... args) {
        getComponent().printAll(args[0].toObject(WrapGraphics.class).getGraphics());
        return Memory.NULL;
    }

    @Signature(@Arg(value = "canvas", typeClass = SwingExtension.NAMESPACE + "Graphics"))
    public Memory paintOne(Environment env, Memory... args) {
        getComponent().paint(args[0].toObject(WrapGraphics.class).getGraphics());
        return Memory.NULL;
    }

    @Signature(@Arg(value = "canvas", typeClass = SwingExtension.NAMESPACE + "Graphics"))
    public Memory paintAll(Environment env, Memory... args) {
        getComponent().paintAll(args[0].toObject(WrapGraphics.class).getGraphics());
        return Memory.NULL;
    }

    @Signature
    public Memory hasFocus(Environment env, Memory... args) {
        return getComponent().hasFocus() ? Memory.TRUE : Memory.FALSE;
    }

    @Signature
    public Memory repaint(Environment env, Memory... args) {
        getComponent().repaint();
        return Memory.NULL;
    }

    @Signature({@Arg("x"), @Arg("y"), @Arg("w"), @Arg("h")})
    public Memory repaintRegion(Environment env, Memory... args) {
        getComponent().repaint(args[0].toInteger(), args[1].toInteger(), args[2].toInteger(), args[3].toInteger());
        return Memory.NULL;
    }

    @Signature
    public Memory grabFocus(Environment env, Memory... args) {
        getJComponent().grabFocus();
        return Memory.NULL;
    }

    @Signature
    public Memory revalidate(Environment env, Memory... args) {
        getJComponent().revalidate();
        return Memory.NULL;
    }

    @Signature(@Arg("uid"))
    public static Memory getByUid(Environment env, Memory... args) {
        ComponentProperties properties = SwingExtension.getProperties(args[0].toString());
        if (properties == null || !properties.isValid())
            return Memory.NULL;

        return new ObjectMemory(UIElement.of(env, properties.getComponent()));
    }

    protected static UIElement unwrap(Memory arg) {
        return ((UIElement) arg.toValue(ObjectMemory.class).value);
    }

    @Signature({@Arg("x"), @Arg("y")})
    public Memory getComponentAt(Environment env, Memory... args) {
        UIElement element = of(env, getComponent().getComponentAt(args[0].toInteger(), args[1].toInteger()));
        return element == null ? Memory.NULL : new ObjectMemory(element);
    }

    @Signature(
            @Arg(value = "menu", typeClass = SwingExtension.NAMESPACE + "UIPopupMenu", optional = @Optional("NULL"))
    )
    protected Memory __setPopupMenu(Environment env, Memory... args) {
        if (args[0].isNull())
            getJComponent().setComponentPopupMenu(null);
        else
            getJComponent().setComponentPopupMenu(args[0].toObject(UIPopupMenu.class).getComponent());
        return Memory.NULL;
    }

    @Signature
    protected Memory __getPopupMenu(Environment env, Memory... args) {
        JPopupMenu menu = getJComponent().getComponentPopupMenu();
        if (menu == null)
            return Memory.NULL;

        return new ObjectMemory(new UIPopupMenu(env, menu));
    }

    @Signature(@Arg("value"))
    protected Memory __setCursor(Environment env, Memory... args) {
        try {
            Field field = Cursor.class.getField(args[0].toString().toUpperCase() + "_CURSOR");
            getComponent().setCursor(Cursor.getPredefinedCursor(field.getInt(null)));
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(e);
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(e);
        }
        return Memory.NULL;
    }

    @Signature({
            @Arg("name"),
            @Arg(value = "callback", type = HintType.CALLABLE, optional = @Optional("null"))
    })
    public Memory setAction(Environment env, Memory... args) {
        if (args[1].isNull()) {
            getJComponent().getActionMap().remove(args[0].toString());
            return Memory.NULL;
        }

        final Invoker invoker = Invoker.valueOf(env, null, args[1]);

        getJComponent().getActionMap().put(args[0].toString(), new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                invoker.callNoThrow();
            }
        });

        return Memory.NULL;
    }

    public Container getOwner() {
        Container owner = getComponent().getParent();

        while (owner != null) {
            for (Component component : owner.getComponents()) {
                if (component == this.getComponent()) {
                    return owner;
                }
            }

            owner = owner.getParent();
        }

        return  null;
    }

    @Signature
    protected Memory __getOwner(Environment env, Memory... args) {
        Container owner = getOwner();

        if (owner == null) {
            return Memory.NULL;
        } else {
            return new ObjectMemory(UIElement.of(env, owner));
        }
    }

    @Signature({
            @Arg("keyString"),
            @Arg("actionName")
    })
    public Memory setInputKey(Environment env, Memory... args) {
        if (args[1].isNull())
            getJComponent().getInputMap().remove(KeyStroke.getKeyStroke(args[0].toString()));
        else
            getJComponent().getInputMap().put(KeyStroke.getKeyStroke(args[0].toString()), args[1].toString());

        return Memory.NULL;
    }

    @Signature(@Arg("str"))
    public Memory getTextWidth(Environment env, Memory... args){
        return LongMemory.valueOf(getComponent().getFontMetrics(getComponent().getFont()).stringWidth(args[0].toString()));
    }

    @Signature
    public Memory getTextHeight(Environment env, Memory... args){
        return LongMemory.valueOf(getComponent().getFontMetrics(getComponent().getFont()).getHeight());
    }

    @Signature
    public void show() {
        getComponent().setVisible(true);
    }

    @Signature
    public void hide() {
        getComponent().setVisible(false);
    }

    @Signature
    public void toggle() {
        if (getComponent().isVisible()) {
            show();
        } else {
            hide();
        }
    }

    @Signature
    public boolean removeSelf() {
        Container owner = getOwner();

        if (owner != null) {
            owner.remove(getComponent());
        }

        return owner != null;
    }

    @Signature
    protected Memory __getCursor(Environment env, Memory... args) {
        return StringMemory.valueOf(getComponent().getCursor().getName());
    }

    public static UIElement of(Environment env, Component component) {
        if (component == null) {
            return null;
        }

        Class<?> clazz = component.getClass();
        Class<? extends UIElement> uiClass = SwingExtension.swingClasses.get(clazz);
        if (uiClass == null)
            uiClass = UIUnknown.class;

        try {
            UIElement el = uiClass.getConstructor(Environment.class, ClassEntity.class)
                    .newInstance(env, env.fetchClass(uiClass));
            el.setComponent(component);
            return el;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
