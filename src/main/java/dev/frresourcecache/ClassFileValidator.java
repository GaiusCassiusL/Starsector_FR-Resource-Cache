package dev.frresourcecache;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class ClassFileValidator {
    private static final int MAGIC = -889275714;
    private static final int ACC_PUBLIC = 0x0001;
    private static final int ACC_STATIC = 0x0008;

    private ClassFileValidator() {
    }

    static boolean isPlausibleResourceHandle(byte[] bytes, String expectedClass, Set<String> requiredMethods,
            Set<String> requiredFieldReferences) {
        try {
            ClassInfo classInfo = ClassFileValidator.parse(bytes);
            return expectedClass.equals(classInfo.thisClass)
                    && "java/io/InputStream".equals(classInfo.superClass)
                    && classInfo.publicClass
                    && classInfo.publicInstanceMethodSignatures.containsAll(requiredMethods)
                    && classInfo.fieldReferences.containsAll(requiredFieldReferences);
        }
        catch (Exception exception) {
            return false;
        }
    }

    static ClassInfo parse(byte[] bytes) throws IOException {
        byte[] utf8Bytes;
        int n;
        int n2;
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
        int n3 = input.readInt();
        if (((long)n3 & 0xFFFFFFFFL) != 3405691582L) {
            throw new IOException("not a class file (bad magic)");
        }
        input.readUnsignedShort();
        input.readUnsignedShort();
        int n4 = input.readUnsignedShort();
        HashMap<Integer, String> hashMap = new HashMap<Integer, String>();
        HashMap<Integer, Integer> hashMap2 = new HashMap<Integer, Integer>();
        HashMap<Integer, int[]> nameAndTypes = new HashMap<Integer, int[]>();
        HashMap<Integer, int[]> fieldReferences = new HashMap<Integer, int[]>();
        block13: for (n2 = 1; n2 < n4; ++n2) {
            n = input.readUnsignedByte();
            switch (n) {
                case 1: {
                    int n5 = input.readUnsignedShort();
                    utf8Bytes = new byte[n5];
                    input.readFully(utf8Bytes);
                    hashMap.put(n2, new String(utf8Bytes, StandardCharsets.UTF_8));
                    continue block13;
                }
                case 7: {
                    hashMap2.put(n2, input.readUnsignedShort());
                    continue block13;
                }
                case 8: {
                    input.readUnsignedShort();
                    continue block13;
                }
                case 9: {
                    fieldReferences.put(n2, new int[]{input.readUnsignedShort(), input.readUnsignedShort()});
                    continue block13;
                }
                case 10:
                case 11: {
                    input.readUnsignedShort();
                    input.readUnsignedShort();
                    continue block13;
                }
                case 12: {
                    nameAndTypes.put(n2, new int[]{input.readUnsignedShort(), input.readUnsignedShort()});
                    continue block13;
                }
                case 3:
                case 4: {
                    input.readInt();
                    continue block13;
                }
                case 5:
                case 6: {
                    input.readLong();
                    ++n2;
                    continue block13;
                }
                case 15: {
                    input.readUnsignedByte();
                    input.readUnsignedShort();
                    continue block13;
                }
                case 16: {
                    input.readUnsignedShort();
                    continue block13;
                }
                case 17:
                case 18: {
                    input.readUnsignedShort();
                    input.readUnsignedShort();
                    continue block13;
                }
                case 19:
                case 20: {
                    input.readUnsignedShort();
                    continue block13;
                }
                default: {
                    throw new IOException("unknown constant pool tag " + n);
                }
            }
        }
        int classAccess = input.readUnsignedShort();
        n2 = input.readUnsignedShort();
        n = input.readUnsignedShort();
        String text = hashMap.get(hashMap2.get(n2));
        String superClass = n == 0 ? null : hashMap.get(hashMap2.get(n));
        int n6 = input.readUnsignedShort();
        input.skipBytes(n6 * 2);
        int n7 = input.readUnsignedShort();
        for (int i = 0; i < n7; ++i) {
            input.readUnsignedShort();
            input.readUnsignedShort();
            input.readUnsignedShort();
            ClassFileValidator.skipAttributes(input);
        }
        HashSet<String> hashSet = new HashSet<String>();
        int n8 = input.readUnsignedShort();
        for (int i = 0; i < n8; ++i) {
            int methodAccess = input.readUnsignedShort();
            int n9 = input.readUnsignedShort();
            int n10 = input.readUnsignedShort();
            if ((methodAccess & ACC_PUBLIC) != 0 && (methodAccess & ACC_STATIC) == 0) {
                hashSet.add(hashMap.get(n9) + ":" + hashMap.get(n10));
            }
            ClassFileValidator.skipAttributes(input);
        }
        HashSet<String> resolvedFieldReferences = new HashSet<>();
        for (Map.Entry<Integer, int[]> entry : fieldReferences.entrySet()) {
            int[] reference = entry.getValue();
            String owner = hashMap.get(hashMap2.get(reference[0]));
            int[] nameAndType = nameAndTypes.get(reference[1]);
            if (owner == null || nameAndType == null) {
                throw new IOException("invalid field reference in constant pool");
            }
            String name = hashMap.get(nameAndType[0]);
            String descriptor = hashMap.get(nameAndType[1]);
            resolvedFieldReferences.add(owner + "." + name + ":" + descriptor);
        }
        return new ClassInfo(
                text,
                superClass,
                (classAccess & ACC_PUBLIC) != 0,
                hashSet,
                resolvedFieldReferences);
    }

    private static void skipAttributes(DataInputStream input) throws IOException {
        int n = input.readUnsignedShort();
        for (int i = 0; i < n; ++i) {
            long l;
            long l2;
            input.readUnsignedShort();
            for (long j = l = (long)input.readInt() & 0xFFFFFFFFL; j > 0L; j -= l2) {
                l2 = input.skip(j);
                if (l2 > 0L) continue;
                if (input.read() < 0) {
                    throw new IOException("truncated class file attribute");
                }
                l2 = 1L;
            }
        }
    }

    static final class ClassInfo {
        final String thisClass;
        final String superClass;
        final boolean publicClass;
        final Set<String> publicInstanceMethodSignatures;
        final Set<String> fieldReferences;

        ClassInfo(String thisClass, String superClass, boolean publicClass,
                Set<String> publicInstanceMethodSignatures, Set<String> fieldReferences) {
            this.thisClass = thisClass;
            this.superClass = superClass;
            this.publicClass = publicClass;
            this.publicInstanceMethodSignatures = publicInstanceMethodSignatures;
            this.fieldReferences = fieldReferences;
        }
    }
}
