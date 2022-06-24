package drawingbot.files.exporters;

import drawingbot.DrawingBotV3;
import drawingbot.files.ExportTask;
import drawingbot.files.FileUtils;
import drawingbot.geom.shapes.IGeometry;
import drawingbot.api.ICanvas;
import drawingbot.javafx.observables.ObservableDrawingPen;
import drawingbot.utils.Limit;
import drawingbot.utils.UnitsLength;

import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.io.File;
import java.io.PrintWriter;

public class GCodeExporter {

    public static final String defaultStartCode =   "G21 (programming in millimeters, mm)" +
                                                    "\n" +
                                                    "G90 (programming in absolute positioning)" +
                                                    "\n" +
                                                    "G28 (auto homing)" +
                                                    "\n" +
                                                    "G1 F8000 (set speed)";
    public static final String defaultEndCode = "";
    public static final String defaultPenDownCode = "G1 Z0";
    public static final String defaultPenUpCode = "G0 Z1";
    public static final String defaultStartLayerCode = "";
    public static final String defaultEndLayerCode = "";


    public static AffineTransform createGCodeTransform(ICanvas canvas, GCodeSettings settings){
        AffineTransform transform = new AffineTransform();

        ///translate by the gcode offset
        transform.translate(settings.getGCodeXOffset(), settings.getGCodeYOffset());

        ///move into print scale
        transform.scale(1/canvas.getPlottingScale(), 1/canvas.getPlottingScale());

//        //g-code y numbers go the other way
//        transform.translate(0, canvas.getScaledHeight());
//
//        //move with pre-scaled offsets
//        transform.translate(canvas.getScaledDrawingOffsetX(), -canvas.getScaledDrawingOffsetY());

        if(settings.gcodeMirror.get()){
            transform.translate(canvas.getScaledWidth(), canvas.getScaledHeight());
            transform.translate(-canvas.getScaledDrawingOffsetX(), -canvas.getScaledDrawingOffsetY());
        } else {
            transform.translate(0, canvas.getScaledHeight());
            transform.translate(canvas.getScaledDrawingOffsetX(), -canvas.getScaledDrawingOffsetY());
        }
        if(settings.gcodeCenterZeroPoint.get()){
            transform.translate(-canvas.getScaledWidth()/2, -canvas.getScaledHeight()/2);
        }

//        //flip y coordinates
//        transform.scale(1, -1);
        if(settings.gcodeMirror.get()){
            transform.scale(-1, -1); // flip X and Y
        } else {
            transform.scale(1, -1); // flip only Y (default)
        }
        return transform;
    }

    public static void exportGCode(ExportTask exportTask, File saveLocation){
        exportGCode(exportTask, DrawingBotV3.INSTANCE.gcodeSettings, saveLocation);
    }

    public static void exportGCode(ExportTask exportTask, GCodeSettings settings, File saveLocation){

        PrintWriter output = FileUtils.createWriter(saveLocation);
        GCodeBuilder builder = new GCodeBuilder(exportTask, settings, output);

        builder.open();

        AffineTransform transform = createGCodeTransform(exportTask.exportDrawing.getCanvas(), settings);

        float[] coords = new float[6];

        for(ObservableDrawingPen drawingPen : exportTask.exportRenderOrder){

            exportTask.exportIterator.reset();
            builder.startLayer(drawingPen.getName());
            while(exportTask.exportIterator.hasNext()){
                IGeometry geometry = exportTask.exportIterator.next();

                if(exportTask.exportIterator.currentPen == drawingPen) {
                    PathIterator iterator;
                    if (settings.gcodeEnableFlattening.get()) {
                        iterator = geometry.getAWTShape().getPathIterator(transform, settings.gcodeCurveFlatness.get());
                    } else {
                        iterator = geometry.getAWTShape().getPathIterator(transform);
                    }

                    while (!iterator.isDone()) {
                        int type = iterator.currentSegment(coords);
                        builder.move(coords, type);
                        iterator.next();
                    }
                    exportTask.onGeometryExported();
                }
            }
            builder.endLayer(drawingPen.getName());
        }
        builder.close();
        DrawingBotV3.logger.info("GCode File Created:  " +  saveLocation);
    }

    public static void exportGCodeTest(ExportTask exportTask, File saveLocation){
        exportGCodeTest(exportTask, DrawingBotV3.INSTANCE.gcodeSettings, saveLocation);
    }

    public static void exportGCodeTest(ExportTask exportTask, GCodeSettings settings, File saveLocation){

        ICanvas canvas = exportTask.exportDrawing.getCanvas();

        float dx_min = 0;
        float dx_max = (float)canvas.getWidth(UnitsLength.MILLIMETRES);
        float dy_min = 0;
        float dy_max = (float)canvas.getHeight(UnitsLength.MILLIMETRES);

        if(settings.gcodeCenterZeroPoint.get()){
            dx_min = dx_min - dx_max/2;
            dx_max = dx_max - dx_max/2;
            dy_min = dy_min - dy_max/2;
            dy_max = dy_max - dy_max/2;
        }

        dx_min = dx_min + settings.getGCodeXOffset();
        dx_max = dx_max + settings.getGCodeXOffset();
        dy_min = dy_min + settings.getGCodeYOffset();
        dy_max = dy_max + settings.getGCodeYOffset();

        String gname = FileUtils.removeExtension(saveLocation) + "gcode_test" + exportTask.extension;
        PrintWriter output = FileUtils.createWriter(new File(gname));
        GCodeBuilder builder = new GCodeBuilder(exportTask, settings, output);

        builder.comment("This is a test file to draw the extremes of the drawing area.");
        builder.comment("Draws a 15 mm mark on all four corners of the paper.");
        builder.comment("WARNING:  pen will be down.");
        builder.comment("Extremes of X: " + dx_min + " thru " + dx_max);
        builder.comment("Extremes of Y: " + dy_min + " thru " + dy_max);

        builder.open();

        float stroke_length = 15;

        builder.comment("Lower left");
        builder.linearMoveG1(dx_min - stroke_length/2, dy_min);
        builder.movePenDown();
        builder.linearMoveG1(dx_min - stroke_length, dy_min);
        builder.linearMoveG1(dx_min - stroke_length, dy_min - stroke_length);
        builder.linearMoveG1(dx_min, dy_min - stroke_length);
        builder.linearMoveG1(dx_min, dy_min - stroke_length/2);
        builder.movePenUp();

        builder.comment("Lower right");
        builder.linearMoveG1(dx_max, dy_min - stroke_length/2);
        builder.movePenDown();
        builder.linearMoveG1(dx_max , dy_min - stroke_length);
        builder.linearMoveG1(dx_max + stroke_length, dy_min - stroke_length);
        builder.linearMoveG1(dx_max + stroke_length, dy_min);
        builder.linearMoveG1(dx_max + stroke_length/2, dy_min);
        builder.movePenUp();

        builder.comment("Upper right");
        builder.linearMoveG1(dx_max + stroke_length/2, dy_max);
        builder.movePenDown();
        builder.linearMoveG1(dx_max + stroke_length, dy_max);
        builder.linearMoveG1(dx_max + stroke_length, dy_max + stroke_length);
        builder.linearMoveG1(dx_max , dy_max + stroke_length);
        builder.linearMoveG1(dx_max, dy_max + stroke_length/2);
        builder.movePenUp();

        builder.comment("Upper left");
        builder.linearMoveG1(dx_min, dy_max + stroke_length/2);
        builder.movePenDown();
        builder.linearMoveG1(dx_min, dy_max + stroke_length);
        builder.linearMoveG1(dx_min - stroke_length, dy_max + stroke_length);
        builder.linearMoveG1(dx_min - stroke_length, dy_max);
        builder.linearMoveG1(dx_min - stroke_length/2, dy_max);
        builder.movePenUp();

        exportTask.updateProgress(1,1);

        builder.close();
    }
//    public static void exportGCodeTest(ExportTask exportTask, GCodeSettings settings, File saveLocation){
//        AffineTransform transform = createGCodeTransform(exportTask.exportDrawing.getCanvas(), settings);
//
//        Limit dx = new Limit(), dy = new Limit();
//
//        float[] coords = new float[6];
//
//        exportTask.exportIterator.reset();
//        while(exportTask.exportIterator.hasNext()){
//            IGeometry geometry = exportTask.exportIterator.next();
//            PathIterator iterator;
//            if(settings.gcodeEnableFlattening.get()){
//                iterator = geometry.getAWTShape().getPathIterator(transform, settings.gcodeCurveFlatness.get() / transform.getScaleX());
//            }else{
//                iterator = geometry.getAWTShape().getPathIterator(transform);
//            }
//            while(!iterator.isDone()){
//                int type = iterator.currentSegment(coords);
//                dx.update_limit(coords[0]);
//                dy.update_limit(coords[1]);
//                iterator.next();
//            }
//            exportTask.onGeometryExported();
//        }
//
//        String gname = FileUtils.removeExtension(saveLocation) + "gcode_test" + exportTask.extension;
//        PrintWriter output = FileUtils.createWriter(new File(gname));
//        GCodeBuilder builder = new GCodeBuilder(exportTask, settings, output);
//
//        builder.comment("This is a test file to draw the extremes of the drawing area.");
//        builder.comment("Draws a 1cm mark on all four corners of the paper.");
//        builder.comment("WARNING:  pen will be down.");
//        builder.comment("Extremes of X: " + dx.min + " thru " + dx.max);
//        builder.comment("Extremes of Y: " + dy.min + " thru " + dy.max);
//
//        builder.open();
//
//        float test_length = 10;
//
//        builder.comment("Upper left");
//        builder.linearMoveG1(dx.min, dy.min + test_length);
//        builder.movePenDown();
//        builder.linearMoveG1(dx.min, dy.min);
//        builder.linearMoveG1(dx.min + test_length, dy.min);
//        builder.movePenUp();
//
//        builder.comment("Upper right");
//        builder.linearMoveG1(dx.max - test_length, dy.min);
//        builder.movePenDown();
//        builder.linearMoveG1(dx.max, dy.min);
//        builder.linearMoveG1(dx.max, dy.min + test_length);
//        builder.movePenUp();
//
//        builder.comment("Lower right");
//        builder.linearMoveG1(dx.max,dy.max - test_length);
//        builder.movePenDown();
//        builder.linearMoveG1(dx.max, dy.max);
//        builder.linearMoveG1(dx.max - test_length, dy.max);
//        builder.movePenUp();
//
//        builder.comment("Lower left");
//        builder.linearMoveG1(dx.min + test_length, dy.max);
//        builder.movePenDown();
//        builder.linearMoveG1(dx.min, dy.max);
//        builder.linearMoveG1(dx.min, dy.max - test_length);
//        builder.movePenUp();
//
//        exportTask.updateProgress(1,1);
//
//        builder.close();
//    }
}

