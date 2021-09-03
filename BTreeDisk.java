import com.sun.codemodel.internal.fmt.JSerializedObject;
import lombok.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;


@Getter @Setter
public class BTreeDisk {
    @Getter @Setter
    static class Metadata {
        int thresholdCount;
        String rootId;

        Metadata() {}

        Metadata(int thresholdCount, String rootId) {
            this.thresholdCount = thresholdCount;
            this.rootId = rootId;
        }
    }
    public int lowerThreshold;
    public int higherThreshold;
    public Metadata metadata;
    public Node root;
    public boolean debugging;
    static String BTREE_METADATA_FILENAME = "btree_metadata";

    @Getter @Setter @AllArgsConstructor
    static class KeyData {
        String key;
        String data;

        @Override
        public String toString() {
            return  key + ":" + data;
        }

        public KeyData copy() {
            return new KeyData(key, data);
        }

        KeyData() {}
    }

    // no need to make this static.
    @Getter @Setter
    class Node {
        boolean inMemory;
        boolean dirty;
        String id;

        List<KeyData> keyDataList = new ArrayList<KeyData>();
        List<Node> childNodes = new ArrayList<Node>();

        String CHILDID_FILE_NAME_FORMAT = "%s.childid";
        String KEYDATA_FILE_NAME_FORMAT = "%s.keydata";

        // Base constructor for new Node
        Node() {
            setId(UUID.randomUUID().toString());
        }

        // Base constructor for either root or node read from disk.
        Node(String childId) {
            setId(childId);
        }

        String getChildIdFile() {
            return String.format(CHILDID_FILE_NAME_FORMAT, id);
        }

        String getKeyDataFile() {
            return String.format(KEYDATA_FILE_NAME_FORMAT, id);
        }

        String[] getChildNodeIds() {
            List<Node> childNodes = getChildNodes();
            String[] childIds = new String[childNodes.size()];

            for (int i = 0; i < childIds.length; i++) {
                childIds[i] = childNodes.get(i).getId();
            }

            return childIds;
        }

        // need a routine to serialize in-memory data into disk.
        void serializeToDisk() throws IOException {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            // write keydata to keydata file.
            String serializedKeyData = objectMapper.writeValueAsString(getKeyDataList().toArray(new KeyData[0]));
            // write child uuids from child file.
            String serializedChildIds = objectMapper.writeValueAsString(getChildNodeIds());

            Files.write(Paths.get(getKeyDataFile()), serializedKeyData.getBytes());
            Files.write(Paths.get(getChildIdFile()), serializedChildIds.getBytes());

            // the memory copy is no longer dirty, assuming this is serialized with other Node modifications.
            setDirty(false);
        }

        // We will only bfs a single node.
        void deserializeFromDisk() throws IOException {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            // read keydata from keydata file.
            byte[] serializedKeyData = Files.readAllBytes(Paths.get(getKeyDataFile()));
            // read child uuids from child file.
            byte[] serializedChildIds = Files.readAllBytes(Paths.get(getChildIdFile()));

            keyDataList.addAll(Arrays.asList(objectMapper.readValue(serializedKeyData, KeyData[].class)));
            String [] childIds = objectMapper.readValue(serializedChildIds, String[].class);
            for (String childId : childIds) {
                addChild(new Node(childId));
            }
            setInMemory(true);
        }

        Boolean isLeafNode() {
            return childNodes.size() == 0;
        }

        int getKeyDataListSize() {
            return getKeyDataList().size();
        }

        int getChildNodesSize() {
            return getChildNodes().size();
        }

        boolean isKeyDataAtHighThreshold() {
            return getKeyDataListSize() == getHigherThreshold();
        }

        boolean isKeyDataAtLowThreshold() {
            return getKeyDataListSize() == getLowerThreshold();
        }

        KeyData getKeyData(int index) {
            return getKeyDataList().get(index);
        }

        KeyData getFirstKeyData() {
            return getKeyData(0);
        }

        KeyData getLastKeyData() {
            return getKeyData(keyDataList.size() - 1);
        }

        String getPrintableKeyData() {
            StringBuilder stringBuilder = new StringBuilder();
            for (KeyData keyData: getKeyDataList()) {
                stringBuilder.append(keyData + ", ");
            }

            return stringBuilder.toString();
        }
        
        Node getChild(int index) {
            List<Node> childNodesList = getChildNodes();

            return childNodesList.size() > 0 ? childNodesList.get(index) : null;
        }

        Node getSibling(int index) {
            return (index < getChildNodesSize() - 1) ? getChild(index + 1) : getChild(index - 1);
        }

        Node deleteAndReturnChild(int index) {
            List<Node> childNodesList = getChildNodes();
            Node child = childNodesList.get(index);
            childNodesList.remove(index);

            return child;
        }

        Node deleteAndReturnFirstChild() {
            return deleteAndReturnChild(0);
        }

        Node deleteAndReturnLastChild() {
            return deleteAndReturnChild(getChildNodesSize() - 1);
        }

        int getIndex(Node child) {
            List<Node> childNodesList = getChildNodes();

            int i;
            for (i = 0; i < childNodesList.size(); i++) {
                if (child == childNodesList.get(i)) {
                    return i;
                }
            }

            return -1;
        }

        void addChild(Node node) {
            List<Node> childNodesList = getChildNodes();

            childNodesList.add(node);
        }

        Node getFirstChild() {
            List<Node> childNodesList = getChildNodes();

            return childNodesList.get(0);
        }

        Node getLastChild() {
            List<Node> childNodesList = getChildNodes();

            return childNodesList.get(childNodesList.size() - 1);
        }

        void insertChild(Node node, int index) {
            List<Node> childNodesList = getChildNodes();

            childNodesList.add(index, node);
        }

        void insertHeadChild(Node child) {
            insertChild(child, 0);
        }

        void insertKeyData(KeyData keyData, int index) {
            List<KeyData> keyDataList = getKeyDataList();

            keyDataList.add(index, keyData);
        }

        void insertHeadKeyData(KeyData keyData) {
            insertKeyData(keyData, 0);
        }

        void addKeyData(KeyData keyData) {
            List<KeyData> keyDataList = getKeyDataList();

            keyDataList.add(keyData);
        }

        KeyData deleteAndReturnKeyData(int index) {
            List<KeyData> keyDataList = getKeyDataList();
            KeyData keyData = keyDataList.get(index);
            keyDataList.remove(index);

            return keyData;
        }

        KeyData deleteAndReturnFirstKeyData() {
            return deleteAndReturnKeyData(0);
        }

        KeyData deleteAndReturnLastKeyData() {
            return deleteAndReturnKeyData(getKeyDataListSize() - 1);
        }

        void deleteChild(int index) {
            List<Node> childNodesList = getChildNodes();

            childNodesList.remove(index);
        }

        void deleteKeyData(int index) {
            List<KeyData> keyDataList = getKeyDataList();

            keyDataList.remove(index);
        }

        void replaceKeyData(KeyData replacement, int index) {
            KeyData keyData = getKeyData(index);
            keyData.setKey(replacement.getKey());
            keyData.setData(replacement.getData());
        }

        List<List<Node>> splitChildList(int index, int keyDataListSize) {
            List<Node> leftChildNodes = getChildNodes().subList(0, index + 1);
            List<Node> rightChildNodes = getChildNodes().subList(index + 1, keyDataListSize + 1);

            List<List<Node>> childListSplitPair = new ArrayList<List<Node>>();
            childListSplitPair.add(new ArrayList<>(leftChildNodes));
            childListSplitPair.add(new ArrayList<>(rightChildNodes));

            return childListSplitPair;
        }

        List<List<KeyData>> splitKeyDataList(int index, int keyDataListSize) {
            List<KeyData> leftKeyData = getKeyDataList().subList(0, index);
            List<KeyData> rightKeyData = getKeyDataList().subList(index + 1, keyDataListSize);

            List<List<KeyData>> keyDataListSplitPair = new ArrayList<List<KeyData>>();
            keyDataListSplitPair.add(new ArrayList<>(leftKeyData));
            keyDataListSplitPair.add(new ArrayList<>(rightKeyData));

            return keyDataListSplitPair;
        }

        void mergeSubTrees(int index) {
            Node node = getChild(index);
            Node sibling = getChild(index + 1);

            KeyData keyData = deleteAndReturnKeyData(index);
            node.getKeyDataList().add(keyData);
            node.getKeyDataList().addAll(sibling.getKeyDataList());
            node.getChildNodes().addAll(sibling.getChildNodes());

            // delete the sibling and zero its references.
            sibling.getKeyDataList().clear();
            sibling.getChildNodes().clear();
            deleteChild(index + 1);

            if (this == getRoot() && getChildNodesSize() == 1) {
                // root is now reduced to a single child, so instead use that child as new root
                setRoot(node);
            }
        }

        SearchData search(String key) {
            int i;
            for (i = 0; i < getKeyDataList().size(); i++) {
                KeyData keyData = getKeyDataList().get(i);
                int compare = keyData.getKey().compareTo(key);
                if (compare > 0) {
                    break;
                } else if (compare < 0) {
                    continue;
                } else {
                    return new SearchData(true, i, -1);
                }
            }

            // exit if you could not find the key in the leaf node.
            return new SearchData(false, -1, i);
        }

        Node getNodeWithLargestKey() {
            if (isLeafNode()) {
                return this;
            }

            return getLastChild().getNodeWithLargestKey();
        }

        Node getNodeWithSmallestKey() {
            if (isLeafNode()) {
                return this;
            }

            return getFirstChild().getNodeWithSmallestKey();
        }
    }

    class SearchData {
        boolean found;
        int keyIndex;
        int childIndex;

        SearchData(boolean found, int keyIndex, int childIndex) {
            this.found = found;
            this.keyIndex = keyIndex;
            this.childIndex = childIndex;
        }
    }

    // This constructs a BTreeDisk.
    BTreeDisk(int thresholdCount) {
        setMetadata(new Metadata(thresholdCount, null));
        setLowerThreshold(metadata.getThresholdCount() - 1);
        setHigherThreshold(2 * metadata.getThresholdCount() - 1);
    }

    // This builds a online BTreeDisk from stored metadata.
    BTreeDisk(Metadata metadata) {
        setMetadata(metadata);
        setLowerThreshold(metadata.getThresholdCount() - 1);
        setHigherThreshold(2 * metadata.getThresholdCount() - 1);
        root = new Node(metadata.getRootId());
        try {
            root.deserializeFromDisk();
        } catch (Exception e) {
            System.out.println("Could not read the root node.");
        }
    }

    static boolean isOnDisk() {
        return Files.exists(Paths.get(BTREE_METADATA_FILENAME));
    }

    void serializeToDisk() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        // The root Id does not need to be kept uptodate until we serialize, so evaluate it here.
        if (getRoot() != null) {
            metadata.setRootId(getRoot().getId());
        } else {
            metadata.setRootId(null);
        }
        String serializedMetadata = objectMapper.writeValueAsString(metadata);
        Files.write(Paths.get(BTREE_METADATA_FILENAME), serializedMetadata.getBytes());
    }

    static BTreeDisk deserializeFromDisk() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        byte[] serializedMetadata = Files.readAllBytes(Paths.get(BTREE_METADATA_FILENAME));
        Metadata metadata = objectMapper.readValue(serializedMetadata, Metadata.class);

        return new BTreeDisk(metadata);
    }

    private int getSubtreeIndexOrInsertLeaf(Node node, KeyData insertKeyData) {
        int i;
        for (i = 0; i < node.getKeyDataList().size(); i++) {
            KeyData keyData = node.getKeyDataList().get(i);
            int compare = keyData.getKey().compareTo(insertKeyData.getKey());
            if (compare < 0) {
                continue;
            } else if (compare > 0) {
                break;
            } else {
                // for duplicates, we just replace current value.
                keyData.setData(insertKeyData.getData());
                return -1;
            }
        }

        if (node.isLeafNode()) {
            node.insertKeyData(insertKeyData, i);
            return -1;
        }

        return i;
    }

    private Node split(Node node, int index) {
        // allocate the sibling node.
        Node sibling = new Node();
        // cache the key data list size as it will be the end marker for sibling lists.
        int keyDataListSize = node.getKeyDataListSize();
        // split the node's keydata and find the median, also split the childnodes.
        List<List<KeyData>> splitKeyDataList = node.splitKeyDataList(index, keyDataListSize);

        // attach the split lists to appropriate nodes.
        node.setKeyDataList(splitKeyDataList.get(0));
        sibling.setKeyDataList(splitKeyDataList.get(1));
        if (!node.isLeafNode()) {
            List<List<Node>> splitChildList = node.splitChildList(index, keyDataListSize);

            node.setChildNodes(splitChildList.get(0));
            sibling.setChildNodes(splitChildList.get(1));
        }
        sibling.setInMemory(true);

        return sibling;
    }

    private Node connectNodeToNewRoot(Node node) {
        Node parent = new Node();
        // we need to reset root here.
        setRoot(parent);
        parent.addChild(node);
        parent.setInMemory(true);

        return parent;
    }

    private void insertAsRoot(KeyData keyData) {
        Node node = new Node();
        node.getKeyDataList().add(keyData);
        setRoot(node);
        node.setInMemory(true);
    }

    private void insert(Node node, Node parent, int nodeIndex, KeyData insertKeyData) throws IOException {
        //               n1-n2-n3
        if (node.isKeyDataAtHighThreshold()) {
            // if the node is the root, create a new root and make node its child.
            if (parent == null) {
                parent = connectNodeToNewRoot(node);
                nodeIndex = 0;
            }

            // split into 2, and push the median above.
            //      n2
            //    n1  n3
            int newSize = (node.getKeyDataListSize() - 1)/ 2;
            KeyData medianKeyData = node.getKeyData(newSize);
            Node sibling = split(node, newSize);
            // attach the previous median and the sibling to the parent.
            parent.insertKeyData(medianKeyData, nodeIndex);
            parent.insertChild(sibling, nodeIndex + 1);

            // after the split we need to reinsert from the parent.
            node = parent;
        }

        // a negative implies the keydata either was added to a leaf node, or overwrote an existing key.
        int i = getSubtreeIndexOrInsertLeaf(node, insertKeyData);
        if (i >= 0) {
            Node child = node.getChild(i);
            if (!child.inMemory) {
                // bring node into memory.
                child.deserializeFromDisk();
            }
            insert(child, node, i, insertKeyData);
        }
    }

    private Node getSibling(Node node, Node parent) {
        int index = parent.getIndex(node);

        if (index > 0) {
            return parent.getChild(index - 1);
        } else {
            return parent.getChild(index + 1);
        }
    }

    private void inorder(Node node, boolean serialize) {
        if (node == null) {
            return;
        }

        List<KeyData> keyDataList = node.getKeyDataList();

        for (int i = 0; i < keyDataList.size(); i++) {
            inorder(node.getChild(i), serialize);
            if (serialize) {
                try {
                    node.serializeToDisk();
                } catch (Exception e) {
                    System.out.println(String.format("Node %s failed serialization.", node.getId()));
                }
            } else {
                System.out.println(keyDataList.get(i));
            }
        }

        // there will be one more child node than the number of keys in this node for every intermediate node.
        inorder(node.getChild(keyDataList.size()), serialize);
    }

    public void insert(String key, String data) throws IOException {
        KeyData keyData = new KeyData(key, data);

        if (getRoot() == null) {
            insertAsRoot(keyData);
        } else {
            insert(getRoot(), null, -1, keyData);
        }
    }

    public boolean delete(String key) {
        Node node = getRoot();

        // every node other than root is pre-vetted for minimum threshold.
        while (!node.isLeafNode()) {
            // check if node has the key.
            SearchData searchData = node.search(key);

            if (searchData.found) {
                // For a successful find, index i and i+1 will both point to valid child nodes.
                // For the last node the only valid sibling is the previous node.
                // verify that this logic works for the last key as well.
                Node child = node.getChild(searchData.keyIndex);
                Node sibling = node.getSibling(searchData.keyIndex);
                //i, i + 1
                if (child.isKeyDataAtLowThreshold() && sibling.isKeyDataAtLowThreshold()) {
                    node.mergeSubTrees(searchData.keyIndex);
                    node = child;
                    continue;
                } else if (!child.isKeyDataAtLowThreshold()) {
                    // we need to find the predecessor in the left subtree. since this will be a leaf.
                    KeyData predecessorKeyData = child.getNodeWithLargestKey().getLastKeyData().copy();
                    node.replaceKeyData(predecessorKeyData, searchData.keyIndex);
                    node = child;
                    key = predecessorKeyData.getKey();
                } else {
                    // we need to find the successor in the right subtree. since this will be a leaf.
                    KeyData successorKeyData = sibling.getNodeWithSmallestKey().getFirstKeyData().copy();
                    node.replaceKeyData(successorKeyData, searchData.keyIndex);
                    node = sibling;
                    key = successorKeyData.getKey();
                }
            } else {
                // here searchdata.keyIndex refers to the child index for subtree where key if present will be found.
                Node child = node.getChild(searchData.childIndex);
                Node sibling = node.getSibling(searchData.childIndex);
                int childKeyIndex = (searchData.childIndex < node.getChildNodesSize() - 1) ?
                        searchData.childIndex : searchData.childIndex - 1;

                if (child.isKeyDataAtLowThreshold() && sibling.isKeyDataAtLowThreshold()) {
                    // if both child and sibling are at lower threshold merge them with the node keydata into new child node.
                    // this will reduce a keydata in the node, but since node is already above low threshold this is fine.
                    node.mergeSubTrees(childKeyIndex);
                    // the merged node will always be the left child of the node, so set it to sibling if it is the left node.
                    if (childKeyIndex == searchData.childIndex) {
                        node = child;
                    } else {
                        node = sibling;
                    }
                } else if (child.isKeyDataAtLowThreshold()) {
                    // we need to ensure child has enough keys for the invariant to be made as child will become node.
                    KeyData keyData = node.getKeyData(childKeyIndex).copy();

                    if (searchData.childIndex < node.getChildNodesSize() - 1) {
                        // rotate left.
                        node.replaceKeyData(sibling.deleteAndReturnFirstKeyData(), childKeyIndex);
                        child.addKeyData(keyData);
                        if (!child.isLeafNode()) {
                            child.addChild(sibling.deleteAndReturnFirstChild());
                        }
                    } else {
                        // rotate right.
                        node.replaceKeyData(sibling.deleteAndReturnLastKeyData(), childKeyIndex);
                        child.insertHeadKeyData(keyData);
                        if (!child.isLeafNode()) {
                            child.insertHeadChild(sibling.deleteAndReturnLastChild());
                        }
                    }
                    // child is the candidate to continue the search into.
                    node = child;
                } else {
                    // child is the candidate to continue the search into.
                    node = child;
                }
            }
        }

        // if the search has concluded in a leaf, its a simple yes/no answer.
        if (node.isLeafNode()) {
            SearchData searchData = node.search(key);
            if (searchData.found) {
                node.deleteAndReturnKeyData(searchData.keyIndex);

                // if this is the last key deleted from a root leaf, then the BTree is empty.
                if (node == getRoot() && node.getKeyDataListSize() == 0) {
                    setRoot(null);
                }

                return true;
            }
        }

        return false;
    }

    public void inorder(boolean serialize) {
        inorder(getRoot(), serialize);
    }

    public static void main(String args[]) throws IOException {
        BTreeDisk bTreeDisk;
        // we serialize the btree on disk on 2 occasions.
        // once on creation and the other time when root changes.
        if (BTreeDisk.isOnDisk()) {
            bTreeDisk = BTreeDisk.deserializeFromDisk();
        } else {
            bTreeDisk = new BTreeDisk(5);
        }

        for (int i = 0; i < 300; i++) {
            bTreeDisk.insert("key" + i, "data" + i);
        }
        bTreeDisk.inorder(true);

        for (int i = 0; i < 100; i++) {
            bTreeDisk.delete("key" + i * 2);
        }
        bTreeDisk.inorder(true);

        bTreeDisk.serializeToDisk();
    }
}
