package com.leakTool;

import static java.util.Collections.emptyMap;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
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
    private static memoryLeakTool instance = null;
    private static ISnapshot snapshot = null;
    protected static List<Object> cache = new ArrayList<Object>();

    public static void main(String[] args) {
        System.out.println("Welcome to memoryLeak tool");
        instance = new memoryLeakTool();
        File file = new File("/home/ritter/Desktop/Hprof/mem");
        try {
        	snapshot = instance.openSnapshot(file);
        	int[] objects = snapshot.getGCRoots();
        	for(int id : objects) {
                IObject o = snapshot.getObject(id);

                

                if (snapshot.isArray(id)) {
                    System.out.println("The object type is " + o.getDisplayName() );
                    int[] inboundRefererIds = snapshot.getInboundRefererIds(id);
                    for (int inboundRefererId: inboundRefererIds) {
                        IObject inboundRefererObjectBuffer = snapshot.getObject(inboundRefererId);
                        System.out.println("--inboundRefererObjectBuffer:" + inboundRefererObjectBuffer.getDisplayName() );

                        System.out.println("============================");
                        IInstance instance = (IInstance)inboundRefererObjectBuffer;
                        fixObjectReferences(snapshot, cache, instance.getFields(), false, false);
                        for (Object object: cache) {
//                            if (object instanceof Integer) {
//                                System.out.println("cache int:" + (Integer)object);
//                            } else if (object instanceof String) {
//                                System.out.println("cache String:" + (String)object);
//                            } else {
                                System.out.println("cache Object:" + object);
//                            }
                        }
                        System.out.println("=============================");

                        int[] inboundRefererBufferIds = snapshot.getInboundRefererIds(inboundRefererId);
                        for (int inboundRefererBufferId: inboundRefererBufferIds) {
                            IObject inboundRefererObjectBitmap = snapshot.getObject(inboundRefererBufferId);
                            System.out.println("----inboundRefererObjectBitmap:" + inboundRefererObjectBitmap.getDisplayName() );
                        }

                        int[] outboundRefererBufferIds = snapshot.getOutboundReferentIds(inboundRefererId);
                        for (int outboundRefererBufferId: outboundRefererBufferIds) {
                            if (snapshot.isArray(outboundRefererBufferId)) {
                                IObject outboundRefererObjectBitmap = snapshot.getObject(outboundRefererBufferId);
                                System.out.println("----outboundRefererObjectBitmap:" + outboundRefererObjectBitmap.getDisplayName());
                                System.out.println("----outboundRefererObjectBitmap RetainedHeapSize:" + outboundRefererObjectBitmap.getRetainedHeapSize());
                                System.out.println("----outboundRefererObjectBitmap UsedHeapSize:" + outboundRefererObjectBitmap.getUsedHeapSize());

                                int width = (Integer)instance.getField("mWidth").getValue();
                                int height = (Integer)instance.getField("mHeight").getValue();
                                System.out.println("----instance.mWidth:" +  width + ",instance.mHeight:" + height);
                                
                                if (outboundRefererObjectBitmap.getUsedHeapSize() > 10000) {
                                    writeRawData(outboundRefererBufferId, outboundRefererObjectBitmap.getObjectId() + "_" + outboundRefererObjectBitmap.getUsedHeapSize() + "_"
                                            + width + "*" + height + "_");
                                }
                            }
                        }
                    }

//                    System.out.println("The object type is " + o.resolveValue(field) );
//                    System.out.println("The object type is " + o.getObjectAddress() );
//                    System.out.println("The object type is " + o.getObjectAddress() );
                }
                //System.out.println("The object type is " + snapshot.isArray(id) );
                //System.out.println("The object type is " + o.getDisplayName() );
        	}
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ISnapshot openSnapshot(File heapDumpFile) throws SnapshotException {
        SnapshotFactory factory = new SnapshotFactory();
        Map<String, String> args = emptyMap();
        VoidProgressListener listener = new VoidProgressListener();
        return factory.openSnapshot(heapDumpFile, args, listener);
    }

    private static void writeRawData(int objectId, String fileName) throws Exception {
        System.out.println("writeRawData objectId:" + objectId + ",fileName:" + fileName);
        IObject object = snapshot.getObject(objectId);
        IPrimitiveArray array = (IPrimitiveArray) object;
        File file = new File("/home/ritter/Desktop/" + fileName + ".rgba");
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

    /*
    protected List<Object> cache = new ArrayList<Object>();
    fixObjectReferences(object.getSnapshot(), cache, object.getFields(), false, false);
    (IInstance) object
*/
    protected static void fixObjectReferences(ISnapshot snapshot,
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
}
