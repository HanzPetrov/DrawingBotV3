package drawingbot.files.json;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import drawingbot.files.json.projects.DBTaskContext;
import drawingbot.javafx.GenericPreset;
import drawingbot.utils.ISpecialListenable;
import javafx.beans.property.BooleanProperty;
import javafx.collections.ObservableList;

import java.util.List;

/**
 * A {@link IPresetLoader} is responsible or loading/saving presets and keeping track of the preset which have been loaded
 * @param <DATA> see {@link #getDataType()} TODO DOCUMENT ME
 *
 *
 *              TODO STILL NEED TO SIMPLIFY SOME MORE, OR CREATE IJSONMANAGER OR SOMETHING (TODO VERY STRICT TESTING) WE CAN'T LOSE USERS PRESETS
 */
public interface IPresetLoader<DATA> extends ISpecialListenable<IPresetLoader.Listener<DATA>> {

    String getVersion();

    /**
     * @return the type of the data held in the preset typically {@link PresetData}, this data must be able to be serialized/deserialized in GSON
     */
    Class<DATA> getDataType();

    /**
     * @return the registered preset type this {@link IPresetLoader} can load/save, important to make sure when a given preset is loaded the correct {@link IPresetLoader} is used
     */
    PresetType getPresetType();

    /**
     * Registers the preset, called when a preset is created or loaded from a json or when a preset is created in the software
     */
    void addPreset(GenericPreset<DATA> preset);

    /**
     * Unregisters the preset, called when a preset is deleted, typically when a preset is deleted by the user
     */
    void removePreset(GenericPreset<DATA> preset);

    /**
     * Should be called after edit operations to confirm them
     * @param oldPreset the original preset which is being edited, must be unchanged during the edit process
     * @param editPreset a copy of the oldPreset which has been altered with the intended edits
     * @return the resulting preset which is still registered, this will return the editPreset if the sub type is changed otherwise it will be the oldPreset
     */
    GenericPreset<DATA> editPreset(GenericPreset<DATA> oldPreset, GenericPreset<DATA> editPreset);

    /**
     * @return a JavaFX {@link ObservableList} of all loaded presets, which is safe for use in UI Components
     */
    ObservableList<GenericPreset<DATA>> getPresets();

    /**
     * @return a JavaFX {@link ObservableList} of all presets which were created by the user, which is safe for use in UI Components, and for saving the presets to GSON
     */
    ObservableList<GenericPreset<DATA>> getUserCreatedPresets();

    /**
     * @return a list of preset which should be saved into the json, typically this is just the {@link #getUserCreatedPresets()}
     */
    default List<GenericPreset<DATA>> getSaveablePresets(){
        return getUserCreatedPresets();
    }

    /**
     * @return a JavaFX {@link ObservableList} of all loaded presets sub types, which is safe for use in UI Components
     */
    ObservableList<String> getPresetSubTypes();

    /**
     * @param subType the preset sub type to retrieve the list for
     * @return a JAVAFX list of all the loaded presets for the given sub type, which is safe for use in UI Components
     */
    ObservableList<GenericPreset<DATA>> getPresetsForSubType(String subType);

    /**
     * INTERNAL USE: Marks the presets as changed. Called by the {@link #addPreset(GenericPreset)}, {@link #removePreset(GenericPreset)} and {@link #editPreset(GenericPreset, GenericPreset)} methods.
     * Avoid calling from outside of the preset manager to prevent excessive json updates     *
     */
    void markDirty();

    /**
     * @return true if the presets have unsaved changes
     */
    boolean isDirty();

    /**
     * @return a property for monitoring if presets have unsaved changes
     */
    BooleanProperty dirtyProperty();

    ////////////////////////////

    /**
     * @return true if the preset loader is still being initialized, during this type json updates will be disabled and listener events won't send
     */
    boolean isLoading();

    /**
     * @return a property to monitor when the preset loader is loaded
     */
    BooleanProperty loadingProperty();

    /**
     * INTERNAL USE: Used during application initializaton to mark when all presets have been loaded, once this point has reached json updates and listener events are activated
     * @param isLoading mark if the loader is loaded
     */
    void setLoading(boolean isLoading);

    ////////////////////////////

    DATA createDataInstance(GenericPreset<DATA> preset);

    /**
     * @return a new {@link GenericPreset} which can be then be updated
     */
    GenericPreset<DATA> createNewPreset();

    /**
     * @param presetSubType the presets sub type
     * @param presetName the presets name
     * @param userCreated true, if the preset should be saved with other user presets
     * @return  a new {@link GenericPreset} with the given parameters
     */
    GenericPreset<DATA> createNewPreset(String presetSubType, String presetName, boolean userCreated);

    /**
     * Used to create a 'safe' preset to perform editing on, typically used when attempting to edit a System Preset
     * @param preset the preset you wish to edit
     * @return an editable version of the preset
     */
    GenericPreset<DATA> createEditablePreset(GenericPreset<DATA> preset);


    ////////////////////////////

    GenericPreset<DATA> getDefaultPreset();

    GenericPreset<DATA> getDefaultPresetForSubType(String subType);

    void setDefaultPreset(GenericPreset<DATA> preset);

    void setDefaultPresetSubType(GenericPreset<DATA> preset);

    void resetDefaultPreset();

    void resetDefaultPresetSubType(String subType);

    ////////////////////////////


    void loadDefaults(DBTaskContext project);

    ////////////////////////////

    void loadFromJSON();

    void updateJSON();

    void saveToJSON();

    DATA fromJsonElement(Gson gson, GenericPreset<?> preset, JsonElement jsonElement);

    JsonElement toJsonElement(Gson gson, GenericPreset<?> src);

    default DATA duplicateData(Gson gson, GenericPreset<DATA> preset){
        return fromJsonElement(gson, preset, toJsonElement(gson, preset));
    }

    ////////////////////////////

    default boolean canLoadPreset(GenericPreset<?> preset){
        return preset != null && preset.presetType == getPresetType();
    }

    default boolean isSystemPreset(GenericPreset<DATA> preset){
        return preset != null && !preset.userCreated;
    }

    default boolean isUserPreset(GenericPreset<DATA> preset){
        return preset != null && preset.userCreated;
    }

    ////////////////////////////

    /**
     * Convenience method to return a preset with a given name
     * @param presetName the preset name to search for
     * @return the found preset with the exact name or null
     */
    default GenericPreset<DATA> findPreset(String presetName){
        for(GenericPreset<DATA> preset : getPresets()){
            if(preset.getPresetName().equals(presetName)){
                return preset;
            }
        }
        return null;
    }

    /**
     * Convenience method to return a preset with a given name
     * @param subType the preset sub type to search for
     * @param presetName the preset name to search for
     * @return the found preset with the exact name or null
     */
    default GenericPreset<DATA> findPreset(String subType, String presetName){
        for(GenericPreset<DATA> preset : getPresets()){
            if(preset.getPresetSubType().equals(subType) && preset.getPresetName().equals(presetName)){
                return preset;
            }
        }
        return null;
    }

    interface Listener<DATA>{

        default void onPresetAdded(GenericPreset<DATA> preset){}

        default void onPresetRemoved(GenericPreset<DATA> preset){}

        default void onPresetEdited(GenericPreset<DATA> preset){}

        default void onMarkDirty(){}
    }
}
