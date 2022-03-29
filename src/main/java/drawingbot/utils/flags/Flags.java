package drawingbot.utils.flags;

import java.util.*;

public class Flags {

    public final static Map<FlagCategory, List<Flag<?>>> ALL_FLAGS = new HashMap<>();

    public final static Flags.FlagCategory RENDER_CATEGORY = new Flags.FlagCategory("RENDER");
    public final static Flags.BooleanFlag FORCE_REDRAW = Flags.addBooleanFlag(RENDER_CATEGORY, new Flags.BooleanFlag(true, false));
    public final static Flags.BooleanFlag TASK_CHANGED = Flags.addBooleanFlag(RENDER_CATEGORY, new Flags.BooleanFlag(false, false));
    public final static Flags.BooleanFlag TASK_CHANGED_STATE = Flags.addBooleanFlag(RENDER_CATEGORY, new Flags.BooleanFlag(false, false));
    public final static Flags.BooleanFlag IMAGE_FILTERS_PARTIAL_UPDATE = Flags.addBooleanFlag(RENDER_CATEGORY, new Flags.BooleanFlag(false, false));
    public final static Flags.BooleanFlag IMAGE_FILTERS_FULL_UPDATE = Flags.addBooleanFlag(RENDER_CATEGORY, new Flags.BooleanFlag(false, false));
    public final static Flags.BooleanFlag CANVAS_CHANGED = Flags.addBooleanFlag(RENDER_CATEGORY, new Flags.BooleanFlag(false, false));
    public final static Flags.BooleanFlag CLEAR_DRAWING = Flags.addBooleanFlag(RENDER_CATEGORY, new Flags.BooleanFlag(false, false));

    public static <T> Flag<T> addFlag(FlagCategory category, Flag<T> flag){
        ALL_FLAGS.putIfAbsent(category, new ArrayList<>());
        ALL_FLAGS.get(category).add(flag);
        return flag;
    }

    public static BooleanFlag addBooleanFlag(FlagCategory category, BooleanFlag flag){
        return (BooleanFlag)addFlag(category, flag);
    }

    public static class FlagCategory {
        public String name;

        public FlagCategory(String name) {
            this.name = name;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof FlagCategory){
                return Objects.equals(((FlagCategory) obj).name, name);
            }
            return super.equals(obj);
        }
    }

    public static class Flag<T> {

        protected Class<T> type;
        protected T resetValue;
        protected T clearValue;

        Flag(Class<T> type, T resetValue, T clearValue){
            this.type = type;
            this.resetValue = resetValue;
            this.clearValue = clearValue;
        }
    }
    
    public static class BooleanFlag extends Flag<Boolean> {

        public BooleanFlag(Boolean initValue, Boolean defaultValue) {
            super(Boolean.class, initValue, defaultValue);
        }
    }

}