package com.leakTool;

import static java.util.Collections.emptyMap;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.mat.SnapshotException;
import org.eclipse.mat.parser.internal.SnapshotFactory;
import org.eclipse.mat.snapshot.ISnapshot;
import org.eclipse.mat.snapshot.model.Field;
import org.eclipse.mat.snapshot.model.IInstance;
import org.eclipse.mat.snapshot.model.IObject;
import org.eclipse.mat.snapshot.model.IPrimitiveArray;
import org.eclipse.mat.snapshot.model.NamedReference;
import org.eclipse.mat.snapshot.model.ObjectReference;
import org.eclipse.mat.ui.snapshot.views.inspector.FieldNode;
import org.eclipse.mat.ui.snapshot.views.inspector.NamedReferenceNode;
import org.eclipse.mat.util.VoidProgressListener;

public class memoryLeakTool {
    private static boolean DEBUG = true;
    private static String CURRENT_DIR = System.getProperty("user.dir");
    private static String OUTPUT_DIR = CURRENT_DIR;
    private static String INPUT_FILE = "";
    private static int MIN_BITMAP_SIZE = 10000;

    private static memoryLeakTool instance = null;
    private static ISnapshot snapshot = null;
    private static List<Object> cache = new ArrayList<Object>();

    public static void main(String[] args) {
        if (!parseArguments(args)) {
            System.out.println("Usage:");
            System.out.println("    memoryLeakTool -i [input file] -o(optional) [output dir] -m(optional) [min bitmap size to dump] -d(optional to debug) [true|false]");
            return;
        }

        print("Welcome to memoryLeak tool v1.1");
        print("Current dir:" + System.getProperty("user.dir"));

        instance = new memoryLeakTool();
        File file = new File(INPUT_FILE);
//        File file = new File("/home/ritter/Desktop/Hprof/mem");
        boolean noException = true;
        try {
        	snapshot = instance.openSnapshot(file);
        	int[] objects = snapshot.getGCRoots();
        	for(int id : objects) {
                IObject o = snapshot.getObject(id);
                /**
                 * Only parse byte[] to get bitmap
                 * */
                if (snapshot.isArray(id) && o.getDisplayName().startsWith("byte[")) {
                    print("The object type is " + o.getDisplayName() );
                    int[] inboundRefererIds = snapshot.getInboundRefererIds(id);
                    for (int inboundRefererId: inboundRefererIds) {
                        IObject inboundRefererObjectBuffer = snapshot.getObject(inboundRefererId);
                        print("--inboundRefererObjectBuffer:" + inboundRefererObjectBuffer.getDisplayName() );

                        if (inboundRefererObjectBuffer.getDisplayName().startsWith("android.graphics.Bitmap")) {
                        } else {
                            print("--inboundRefererObjectBuffer DisplayName not android.graphics.Bitmap, continue:");
                            continue;
                        }

                        print("============================");
                        IInstance instance = (IInstance)inboundRefererObjectBuffer;
                        fixObjectReferences(snapshot, cache, instance.getFields(), false, false);
                        for (Object object: cache) {
                            print("cache Object:" + object);
                        }
                        print("=============================");

                        int[] inboundRefererBufferIds = snapshot.getInboundRefererIds(inboundRefererId);
                        for (int inboundRefererBufferId: inboundRefererBufferIds) {
                            IObject inboundRefererObjectBitmap = snapshot.getObject(inboundRefererBufferId);
                            print("----inboundRefererObjectBitmap:" + inboundRefererObjectBitmap.getDisplayName() );
                        }

                        int[] outboundRefererBufferIds = snapshot.getOutboundReferentIds(inboundRefererId);
                        for (int outboundRefererBufferId: outboundRefererBufferIds) {
                            if (snapshot.isArray(outboundRefererBufferId)) {
                                IObject outboundRefererObjectBuffer = snapshot.getObject(outboundRefererBufferId);
                                print("----outboundRefererObjectBuffer:" + outboundRefererObjectBuffer.getDisplayName());
                                print("----outboundRefererObjectBuffer RetainedHeapSize:" + outboundRefererObjectBuffer.getRetainedHeapSize());
                                print("----outboundRefererObjectBuffer UsedHeapSize:" + outboundRefererObjectBuffer.getUsedHeapSize());

                                int width = (Integer)instance.getField("mWidth").getValue();
                                int height = (Integer)instance.getField("mHeight").getValue();
                                String displayName = outboundRefererObjectBuffer.getDisplayName();
                                String address = outboundRefererObjectBuffer.getDisplayName().substring(displayName.indexOf("@") + 2, displayName.indexOf("@") + 12);
                                print("----instance.mWidth:" +  width + ",instance.mHeight:" + height + ",address:" + address);
                                if (outboundRefererObjectBuffer.getUsedHeapSize() > MIN_BITMAP_SIZE) {
                                    writeRawData(outboundRefererBufferId, address + "_" + outboundRefererObjectBuffer.getUsedHeapSize() + "_"
                                            + width + "*" + height, ".rgba");
                                }
                            }
                        }
                    }
                }
        	}
        } catch (Exception e) {
            print("main Exception e:" + e.getMessage());
            if ("SnapshotFactoryImpl_Error_NoParserRegistered".equalsIgnoreCase(e.getMessage())) {
                noException = false;
                createExceptionFlag("NoParserRegistered");
            }
            e.printStackTrace();
        } finally {
            print("main noException:" + noException);
        }
    }

    private ISnapshot openSnapshot(File heapDumpFile) throws SnapshotException {
        SnapshotFactory factory = new SnapshotFactory();
        Map<String, String> args = emptyMap();
        VoidProgressListener listener = new VoidProgressListener();
        return factory.openSnapshot(heapDumpFile, args, listener);
    }

    private static void writeRawData(int objectId, String fileName, String postfix) throws Exception {
        print("writeRawData objectId:" + objectId + ",fileName:" + fileName);
        IObject object = snapshot.getObject(objectId);
        IPrimitiveArray array = (IPrimitiveArray) object;
        File file = new File(OUTPUT_DIR + "/" + fileName + postfix);
        FileOutputStream out = null;

        try
        {
            out = new FileOutputStream(file);
            DataOutputStream writer = new DataOutputStream(new BufferedOutputStream(out));
            try 
            {
                int size = array.getLength();

                int offset = 0;

                while (offset < size)
                {
                    int read = Math.min(4092, size - offset);
                    Object valueArray = array.getValueArray(offset, read);

                    switch (array.getType())
                    {
                        case IObject.Type.BOOLEAN:
                        {
                            boolean[] a = (boolean[]) valueArray;
                            for (int ii = 0; ii < a.length; ii++)
                                writer.writeBoolean(a[ii]);
                            break;
                        }
                        case IObject.Type.BYTE:
                        {
                            byte[] a = (byte[]) valueArray;
                            writer.write(a);
                            break;
                        }
                        case IObject.Type.CHAR:
                        {
                            char[] a = (char[]) valueArray;
                            for (int ii = 0; ii < a.length; ii++)
                                writer.writeChar(a[ii]);
                            break;
                        }
                        case IObject.Type.DOUBLE:
                        {
                            double[] a = (double[]) valueArray;
                            for (int ii = 0; ii < a.length; ii++)
                                writer.writeDouble(a[ii]);
                            break;
                        }
                        case IObject.Type.FLOAT:
                        {
                            float[] a = (float[]) valueArray;
                            for (int ii = 0; ii < a.length; ii++)
                                writer.writeFloat(a[ii]);
                            break;
                        }
                        case IObject.Type.INT:
                        {
                            int[] a = (int[]) valueArray;
                            for (int ii = 0; ii < a.length; ii++)
                                writer.writeInt(a[ii]);
                            break;
                        }
                        case IObject.Type.LONG:
                        {
                            long[] a = (long[]) valueArray;
                            for (int ii = 0; ii < a.length; ii++)
                                writer.writeLong(a[ii]);
                            break;
                        }
                        case IObject.Type.SHORT:
                        {
                            short[] a = (short[]) valueArray;
                            for (int ii = 0; ii < a.length; ii++)
                                writer.writeShort(a[ii]);
                            break;
                        }
                        default:
                            throw new SnapshotException("1111");
                    }
                    offset += read;
                }

                writer.flush();
            }
            finally
            {
                writer.close();
            }
        }
        finally
        {
            if (out != null) {
                out.close();
            }
        }
    }

    private static void createExceptionFlag(String fileName){
        print("createExceptionFlagd:" + ",fileName:" + fileName);
        File file = new File(OUTPUT_DIR + "/" + fileName);
        FileOutputStream out = null;

        try
        {
            out = new FileOutputStream(file);
            DataOutputStream writer = new DataOutputStream(new BufferedOutputStream(out));
            writer.flush();
            writer.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

    /*
    protected List<Object> cache = new ArrayList<Object>();
    fixObjectReferences(object.getSnapshot(), cache, object.getFields(), false, false);
    (IInstance) object
     */

    private static void fixObjectReferences(ISnapshot snapshot,
            List<Object> appendTo, List<Field> fields, boolean areStatics,
            boolean showPseudoStatics) {
        for (int ii = 0; ii < fields.size(); ii++) {
            Field field = fields.get(ii);

            // Do we want to show pseudo static fields?
            if (areStatics
                    && (field.getName().startsWith("<") != showPseudoStatics))continue; //$NON-NLS-1$

            if (field.getValue() instanceof ObjectReference) {
                ObjectReference ref = (ObjectReference) field.getValue();
                if (ref != null) {
                    appendTo.add(new NamedReferenceNode(new NamedReference(
                            snapshot, ref.getObjectAddress(), field.getName()),
                            areStatics));
                } else {
                    Field f = new Field(field.getName(), field.getType(),
                            "null"); //$NON-NLS-1$
                    appendTo.add(new FieldNode(f, areStatics));
                }
            } else {
                appendTo.add(new FieldNode(field, areStatics));
            }
        }
    }

    private static void print(String x) {
        if (DEBUG) {
            System.out.println(x);
        }
    }

    private static boolean parseArguments(String[] args) {
        try {
            for (int i = 0; i < args.length; ++i) {
                if ("-i".equals(args[i])) {
                    ++i;
                    INPUT_FILE = args[i];
                    System.out.println("INPUT_FILE:" + INPUT_FILE);
                } else if ("-o".equals(args[i])) {
                    ++i;
                    OUTPUT_DIR = args[i];
                    System.out.println("OUTPUT_DIR:" + OUTPUT_DIR);
                } else if ("-m".equals(args[i])) {
                    ++i;
                    MIN_BITMAP_SIZE = Integer.parseInt(args[i]);
                    System.out.println("MIN_BITMAP_SIZE:" + MIN_BITMAP_SIZE);
                } else if ("-d".equals(args[i])) {
                    ++i;
                    DEBUG = Boolean.parseBoolean(args[i]);
                    System.out.println("DEBUG:" + DEBUG);
                }
            }
            System.out.println();
            if (INPUT_FILE.isEmpty()) {
                return false;
            }
        } catch (Exception e) {
            System.out.println("Exception:" + e.getMessage());
            return false;
        }
        return true;
    }
}
