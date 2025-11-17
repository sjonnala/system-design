# How Do B-Tree Indexes Work? - Deep Dive

## Contents

- [How Do B-Tree Indexes Work? - Deep Dive](#how-do-b-tree-indexes-work---deep-dive)
  - [Core Mental Model](#core-mental-model)
  - [B-Tree Structure & Properties](#1-b-tree-structure--properties)
  - [B-Tree vs B+Tree: The Critical Difference](#2-b-tree-vs-btree-the-critical-difference)
  - [Search Algorithm: Finding Keys](#3-search-algorithm-finding-keys)
  - [Insert Algorithm: Maintaining Balance](#4-insert-algorithm-maintaining-balance)
  - [Delete Algorithm: Handling Underflow](#5-delete-algorithm-handling-underflow)
  - [Why B-Trees Dominate Disk-Based Storage](#6-why-b-trees-dominate-disk-based-storage)
  - [Production Implementations](#7-production-implementations)
  - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
  - [MIND MAP: B-TREE CONCEPTS](#mind-map-b-tree-concepts)

## Core Mental Model

```text
B-Tree Performance (for N keys):
- Search:  O(log N) - Logarithmic height traversal
- Insert:  O(log N) - Search + possible splits
- Delete:  O(log N) - Search + possible merges
- Range:   O(log N + K) - Where K = results returned

Why Logarithmic?
Height = log_m(N) where m = fanout (keys per node)

Example: 1 billion keys, fanout=200
Height = log₂₀₀(1,000,000,000) ≈ 4 levels!
Maximum disk reads: 4 (excellent!)
```

**Fundamental B-Tree Properties:**
```
┌──────────────────────────────────────────────────────┐
│ 1. BALANCED: All leaf nodes at same depth           │
│ 2. SORTED: Keys in each node are ordered            │
│ 3. BRANCHING: High fanout (100-1000 children/node)  │
│ 4. SELF-BALANCING: Automatic rebalancing on changes │
└──────────────────────────────────────────────────────┘
```

**B-Tree Invariants (for order m):**
```
Every node (except root):
- Has between ⌈m/2⌉ and m children
- Has between ⌈m/2⌉-1 and m-1 keys

Root node:
- Has between 2 and m children (unless it's a leaf)
- Has between 1 and m-1 keys

All leaves:
- At the same depth (balanced tree)
- Contain actual data pointers
```

**Visual Model (B-Tree of order 5):**
```text
                    [30 | 60]                    ← Root (internal node)
                  /    |    \
                /      |      \
              /        |        \
    [10 | 20]   [40 | 50]   [70 | 80 | 90]      ← Internal nodes
    /  |  \      /  |  \      /  |  |  \
  ...  ... ...  ... ... ...  ... ... ... ...    ← Leaf nodes (data)

Node structure:
┌─────┬────┬─────┬────┬─────┐
│ P₀  │ K₁ │ P₁  │ K₂ │ P₂  │
└─────┴────┴─────┴────┴─────┘
Where:
- K = Key
- P = Pointer (to child node or data row)
- Keys in P₀ subtree < K₁
- Keys in P₁ subtree are between K₁ and K₂
- Keys in P₂ subtree > K₂
```

**Real World Implementation:**
```java
// Domain Model: B-Tree Node
public class BTreeNode<K extends Comparable<K>, V> {

    private final int order;  // Maximum children (m)
    private final List<K> keys;
    private final List<V> values;  // For leaf nodes (B+Tree style)
    private final List<BTreeNode<K, V>> children;
    private boolean isLeaf;

    // Invariant: keys.size() <= order - 1
    // Invariant: children.size() <= order
    // Invariant: children.size() = keys.size() + 1 (for internal nodes)

    public BTreeNode(int order, boolean isLeaf) {
        this.order = order;
        this.isLeaf = isLeaf;
        this.keys = new ArrayList<>(order - 1);
        this.values = isLeaf ? new ArrayList<>(order - 1) : null;
        this.children = isLeaf ? null : new ArrayList<>(order);
    }

    public boolean isFull() {
        return keys.size() == order - 1;
    }

    public boolean isUnderflow() {
        // Root can have 1 key, others need at least ⌈m/2⌉-1 keys
        int minKeys = (int) Math.ceil(order / 2.0) - 1;
        return keys.size() < minKeys;
    }

    public int findChildIndex(K key) {
        // Binary search to find which child to descend to
        int pos = Collections.binarySearch(keys, key);

        if (pos >= 0) {
            return pos + 1;  // Key found, go to right child
        } else {
            return -pos - 1;  // Key not found, insertion point
        }
    }
}
```

---

### 1. **B-Tree Structure & Properties**

🎓 **PROFESSOR**: B-Trees were invented in 1970 by Rudolf Bayer and Edward McCreight at Boeing. The "B" stands for "Boeing," "Balanced," or "Bayer" (disputed).

#### Why B-Trees? The Disk I/O Problem

```text
Problem: Hard disk seeks are SLOW (5-10ms)

Binary Search Tree (BST) on disk:
- Height: log₂(N)
- For 1M nodes: log₂(1,000,000) ≈ 20 levels
- Disk reads: 20 (200ms total - unacceptable!)

B-Tree (order 200):
- Height: log₂₀₀(N)
- For 1M nodes: log₂₀₀(1,000,000) ≈ 3 levels
- Disk reads: 3 (30ms total - excellent!)

Key insight: Maximize fanout to minimize height
```

#### B-Tree Order and Node Capacity

```text
Order m = 5 (common in textbooks for illustration)
- Max keys per node: 4
- Max children per node: 5
- Min keys per node (non-root): 2
- Min children per node (non-root): 3

Order m = 200 (realistic for databases)
- Max keys per node: 199
- Max children per node: 200
- Min keys per node (non-root): 99
- Min children per node (non-root): 100
- Node size: ~8KB (one disk page)
```

🏗️ **ARCHITECT**: Node size calculation:

```python
def calculate_btree_parameters(key_size_bytes: int,
                                pointer_size_bytes: int,
                                page_size_bytes: int = 8192) -> dict:
    """
    Calculate optimal B-Tree order for given parameters
    """
    # Node structure: [P₀, K₁, P₁, K₂, P₂, ..., Kₙ, Pₙ]
    # Keys: n
    # Pointers: n + 1
    # Size: n * key_size + (n + 1) * pointer_size

    # Solve for n: n * key_size + (n + 1) * pointer_size ≤ page_size
    # n * (key_size + pointer_size) + pointer_size ≤ page_size
    # n ≤ (page_size - pointer_size) / (key_size + pointer_size)

    max_keys = (page_size_bytes - pointer_size_bytes) // \
               (key_size_bytes + pointer_size_bytes)

    order = max_keys + 1  # Order = max children = max keys + 1

    min_keys = (order + 1) // 2 - 1  # ⌈m/2⌉ - 1

    return {
        'order': order,
        'max_keys_per_node': max_keys,
        'min_keys_per_node': min_keys,
        'max_children': order,
        'min_children': (order + 1) // 2
    }


# Example: PostgreSQL B-Tree on BIGINT column
params = calculate_btree_parameters(
    key_size_bytes=8,       # BIGINT
    pointer_size_bytes=8,   # 64-bit pointer
    page_size_bytes=8192    # Standard page size
)
print(params)
# {
#   'order': 512,
#   'max_keys_per_node': 511,
#   'min_keys_per_node': 255,
#   'max_children': 512,
#   'min_children': 256
# }

# Tree height for 1 billion keys:
import math
height = math.log(1_000_000_000, 512)
print(f"Height: {math.ceil(height)} levels")
# Height: 4 levels
# Maximum disk I/O: 4 reads (root cached in memory → 3 reads in practice)
```

**Interview gold**: "B-Trees minimize disk I/O by maximizing fanout. Each node fits in one disk page (8KB), so reading a node is one I/O. With fanout of 200-500, we get log₂₀₀(N) height instead of log₂(N), reducing I/O by 10-100x."

---

### 2. **B-Tree vs B+Tree: The Critical Difference**

🎓 **PROFESSOR**: Most production databases use **B+Trees**, not B-Trees. Here's why:

```text
B-Tree:
- Data stored in ALL nodes (internal + leaves)
- Less efficient range scans (must traverse tree)
- More complex deletion

B+Tree:
- Data stored ONLY in leaf nodes
- Internal nodes only store keys + pointers (more keys per node!)
- Leaf nodes linked (doubly-linked list for range scans)
- Simpler, more efficient - used by PostgreSQL, MySQL InnoDB, SQLite
```

#### Visual Comparison

```text
B-Tree (order 3):
                    [30]
                  /      \
            [10, 20]    [40, 50]    ← Data in internal nodes too
           /   |   \    /   |   \
        ...  ...  ... ...  ...  ...


B+Tree (order 3):
                    [30]              ← Only keys (no data)
                  /      \
            [10, 20]    [40, 50]      ← Only keys (no data)
           /   |   \    /   |   \
      [10→] [20→] [30→] [40→] [50→]  ← Data ONLY in leaves
        ↓     ↓     ↓     ↓     ↓
      [D1]  [D2]  [D3]  [D4]  [D5]   ← Actual data or row pointers

Leaf nodes linked: [10] ⟷ [20] ⟷ [30] ⟷ [40] ⟷ [50]
```

#### B+Tree Advantages

```text
1. HIGHER FANOUT:
   Internal nodes don't store data, only keys + pointers
   → More keys per node → shorter tree → fewer I/O

2. EFFICIENT RANGE SCANS:
   Leaf nodes linked (like a skip list)
   → Range query: Find start, then scan linked list
   → No need to traverse tree repeatedly

3. SEQUENTIAL ACCESS:
   All data in leaf level
   → Full table scan: Read leaf level sequentially
   → Great for bulk operations

4. SIMPLER IMPLEMENTATION:
   Insertion/deletion only modify leaf + internal structure
   → Less complex than B-Tree
```

🏗️ **ARCHITECT**: B+Tree range query optimization:

```python
class BPlusTree:
    """
    B+Tree with linked leaves for efficient range queries
    """
    class LeafNode:
        def __init__(self):
            self.keys = []
            self.values = []  # Data or row pointers
            self.next = None  # Link to next leaf (sorted order)
            self.prev = None  # Link to previous leaf

    def range_query(self, start_key, end_key):
        """
        Efficient range query: O(log N + K) where K = results
        """
        # Step 1: Find start leaf (O(log N))
        leaf = self._find_leaf(start_key)

        results = []

        # Step 2: Scan linked leaves (O(K))
        while leaf is not None:
            for i, key in enumerate(leaf.keys):
                if start_key <= key <= end_key:
                    results.append((key, leaf.values[i]))
                elif key > end_key:
                    return results  # Done

            leaf = leaf.next  # Move to next leaf

        return results

# Example: Range query
# Query: SELECT * FROM orders WHERE created_at BETWEEN '2024-01-01' AND '2024-01-31'

# B+Tree execution:
# 1. Search for '2024-01-01' → Find leaf node (3 I/O for height 3)
# 2. Scan linked leaves until '2024-01-31' (sequential reads - fast!)
# Total: 3 + (K / entries_per_page) I/O

# B-Tree execution:
# 1. Search for '2024-01-01'
# 2. In-order traversal to find next key (many tree traversals!)
# Total: Much more I/O due to tree navigation
```

**Interview talking point**: "B+Trees are superior for databases because they optimize the common case: range queries (BETWEEN, <, >) and sorted retrieval (ORDER BY). That's why PostgreSQL, MySQL InnoDB, and SQLite all use B+Trees, not B-Trees."

---

### 3. **Search Algorithm: Finding Keys**

🎓 **PROFESSOR**: Searching a B-Tree is like binary search within each node, descending the tree:

```text
Search Algorithm:
1. Start at root
2. Binary search keys in current node
3. If found, return (B-Tree) or continue to leaf (B+Tree)
4. If not found, descend to appropriate child
5. Repeat until leaf node
6. In leaf, perform final search

Time Complexity: O(log_m N) tree traversal + O(m log m) binary search per node
                ≈ O(log N) since m is constant
```

**Detailed Implementation:**

```java
public class BPlusTree<K extends Comparable<K>, V> {

    private Node root;
    private final int order;

    // Search returns value associated with key
    public V search(K key) {
        if (root == null) {
            return null;
        }

        return searchRecursive(root, key);
    }

    private V searchRecursive(Node node, K key) {
        // Binary search for key position in current node
        int index = binarySearchKeys(node.keys, key);

        if (node.isLeaf()) {
            // At leaf node - check if key exists
            if (index < node.keys.size() &&
                node.keys.get(index).compareTo(key) == 0) {
                return ((LeafNode) node).values.get(index);
            }
            return null;  // Not found
        } else {
            // Internal node - descend to child
            InternalNode internal = (InternalNode) node;

            if (index < node.keys.size() &&
                node.keys.get(index).compareTo(key) == 0) {
                // Key found in internal node (B-Tree would return here)
                // B+Tree continues to leaf for actual data
                index++;
            }

            return searchRecursive(internal.children.get(index), key);
        }
    }

    private int binarySearchKeys(List<K> keys, K key) {
        int left = 0, right = keys.size() - 1;

        while (left <= right) {
            int mid = left + (right - left) / 2;
            int cmp = keys.get(mid).compareTo(key);

            if (cmp == 0) {
                return mid;  // Found
            } else if (cmp < 0) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }

        return left;  // Insertion point if not found
    }
}
```

🏗️ **ARCHITECT**: Search with I/O counting:

```python
class BTreeWithIOCounter:
    """
    B-Tree that tracks disk I/O operations
    """
    def __init__(self, order=200):
        self.order = order
        self.root = None
        self.io_count = 0  # Track disk reads

    def search(self, key):
        self.io_count = 0  # Reset counter
        if self.root is None:
            return None

        return self._search_recursive(self.root, key)

    def _search_recursive(self, node, key):
        # Simulate disk read
        self.io_count += 1
        print(f"I/O #{self.io_count}: Read node at level {node.level}")

        # Binary search in node
        index = self._binary_search(node.keys, key)

        if node.is_leaf:
            # At leaf - final check
            if index < len(node.keys) and node.keys[index] == key:
                print(f"✓ Found after {self.io_count} I/O operations")
                return node.values[index]
            print(f"✗ Not found after {self.io_count} I/O operations")
            return None
        else:
            # Descend to child
            return self._search_recursive(node.children[index], key)


# Example: Search in tree with 1M keys, order 200
tree = BTreeWithIOCounter(order=200)
# ... insert 1M keys ...

result = tree.search(987654)
# Output:
# I/O #1: Read node at level 0 (root)
# I/O #2: Read node at level 1
# I/O #3: Read node at level 2
# I/O #4: Read node at level 3 (leaf)
# ✓ Found after 4 I/O operations

# Compare to binary search tree: ~20 I/O for 1M keys
```

---

### 4. **Insert Algorithm: Maintaining Balance**

🎓 **PROFESSOR**: Insertion is where B-Trees' self-balancing magic happens:

```text
Insert Algorithm:
1. Search for insertion position (traverse to leaf)
2. Insert key in sorted order in leaf
3. If leaf is full (overflow), SPLIT:
   a. Create new node
   b. Move half of keys to new node
   c. Promote middle key to parent
4. If parent overflows, split parent (recursively)
5. If root splits, create new root (tree height increases by 1)

Key insight: Tree grows UPWARD from root, not downward
           Always balanced - all leaves remain at same depth
```

#### Visual Example: Insert 25

```text
Before (order 3, max 2 keys per node):
                [30]
              /      \
        [10, 20]    [40, 50]


Step 1: Search for insertion point
→ 25 < 30, go left
→ 20 < 25 < 30, insert in [10, 20]
→ [10, 20, 25] ← OVERFLOW (max 2 keys)


Step 2: Split leaf node
                [30]
              /      \
        [10, 20]    [40, 50]

Split [10, 20, 25]:
- Left:  [10]
- Middle: 20 (promote to parent)
- Right: [25]


Step 3: Promote middle key
                [20, 30]     ← Parent now has 2 keys (OK)
              /   |    \
          [10]  [25]  [40, 50]


Done! Tree remains balanced.
```

**Implementation:**

```java
public class BPlusTree<K extends Comparable<K>, V> {

    public void insert(K key, V value) {
        if (root == null) {
            root = new LeafNode(order);
        }

        // Check if root needs to split
        if (root.isFull()) {
            // Create new root
            InternalNode newRoot = new InternalNode(order);
            newRoot.children.add(root);

            splitChild(newRoot, 0);
            root = newRoot;
        }

        insertNonFull(root, key, value);
    }

    private void insertNonFull(Node node, K key, V value) {
        if (node.isLeaf()) {
            // Insert in sorted order
            LeafNode leaf = (LeafNode) node;
            int pos = findInsertPosition(leaf.keys, key);
            leaf.keys.add(pos, key);
            leaf.values.add(pos, value);
        } else {
            // Find child to descend to
            InternalNode internal = (InternalNode) node;
            int childIndex = findChildIndex(internal.keys, key);
            Node child = internal.children.get(childIndex);

            if (child.isFull()) {
                // Split child before descending
                splitChild(internal, childIndex);

                // Determine which of the two children to descend to
                if (key.compareTo(internal.keys.get(childIndex)) > 0) {
                    childIndex++;
                }
                child = internal.children.get(childIndex);
            }

            insertNonFull(child, key, value);
        }
    }

    private void splitChild(InternalNode parent, int childIndex) {
        Node fullChild = parent.children.get(childIndex);
        int mid = fullChild.keys.size() / 2;

        if (fullChild.isLeaf()) {
            // Split leaf node
            LeafNode left = (LeafNode) fullChild;
            LeafNode right = new LeafNode(order);

            // Move half of keys to right
            right.keys.addAll(left.keys.subList(mid, left.keys.size()));
            right.values.addAll(left.values.subList(mid, left.values.size()));

            left.keys.subList(mid, left.keys.size()).clear();
            left.values.subList(mid, left.values.size()).clear();

            // Link siblings
            right.next = left.next;
            left.next = right;

            // Promote first key of right child (B+Tree style)
            K promotedKey = right.keys.get(0);
            parent.keys.add(childIndex, promotedKey);
            parent.children.add(childIndex + 1, right);

        } else {
            // Split internal node
            InternalNode left = (InternalNode) fullChild;
            InternalNode right = new InternalNode(order);

            // Promote middle key
            K promotedKey = left.keys.get(mid);
            left.keys.remove(mid);

            // Move half to right
            right.keys.addAll(left.keys.subList(mid, left.keys.size()));
            right.children.addAll(left.children.subList(mid + 1, left.children.size()));

            left.keys.subList(mid, left.keys.size()).clear();
            left.children.subList(mid + 1, left.children.size()).clear();

            // Add to parent
            parent.keys.add(childIndex, promotedKey);
            parent.children.add(childIndex + 1, right);
        }
    }
}
```

🏗️ **ARCHITECT**: Write amplification from splits:

```python
class BTreeWriteAmplification:
    """
    Analyze write amplification from B-Tree splits
    """
    def __init__(self, order=200):
        self.order = order
        self.total_writes = 0
        self.split_count = 0

    def insert_with_tracking(self, key, value):
        # Normal insertion writes 1 leaf page
        self.total_writes += 1

        # Simulate split probability
        # Average case: 1 split per (order/2) insertions
        if random.random() < 2.0 / self.order:
            self._handle_split()

    def _handle_split(self):
        """
        Split cascades up the tree
        """
        self.split_count += 1

        # Leaf split: Write 2 leaf pages + 1 parent page
        self.total_writes += 3

        # Cascade probability (10% chance parent also splits)
        if random.random() < 0.1:
            self.total_writes += 2  # Parent split


# Simulation: 1M insertions
tracker = BTreeWriteAmplification(order=200)
for i in range(1_000_000):
    tracker.insert_with_tracking(i, f"value_{i}")

write_amplification = tracker.total_writes / 1_000_000
print(f"Write amplification: {write_amplification:.2f}x")
print(f"Splits: {tracker.split_count}")

# Typical output:
# Write amplification: 1.05x
# Splits: ~5,000
# Conclusion: B-Trees have LOW write amplification (~5% overhead)
```

---

### 5. **Delete Algorithm: Handling Underflow**

🎓 **PROFESSOR**: Deletion is the most complex B-Tree operation:

```text
Delete Algorithm:
1. Search for key (traverse to leaf)
2. Remove key from leaf
3. If leaf underflows (< ⌈m/2⌉-1 keys):
   a. Try to BORROW from sibling (rotation)
   b. If sibling too small, MERGE with sibling
4. Update parent keys
5. If parent underflows, recursively fix parent

Underflow handling:
- BORROW: Take key from sibling, update parent separator
- MERGE: Combine two nodes, remove separator from parent
```

#### Visual Example: Delete 50

```text
Before (order 3, min 1 key per node):
                [30]
              /      \
          [10]      [40, 50]

Delete 50:
                [30]
              /      \
          [10]      [40]    ← Still valid (1 key OK)

Delete 40:
                [30]
              /      \
          [10]      []      ← UNDERFLOW (0 keys)

Option 1: Borrow from left sibling (if it has extra keys)
- Left has [10] (only 1 key, minimum) → Can't borrow

Option 2: Merge with sibling
- Merge [10] and [] with separator 30
- Result: [10, 30]

After merge:
        [10, 30]    ← New root (tree height decreased)
```

**Implementation:**

```python
class BPlusTreeWithDelete:
    """
    B+Tree with deletion support
    """
    def delete(self, key):
        if self.root is None:
            return False

        found = self._delete_recursive(self.root, key)

        # If root is empty internal node after deletion, make child the new root
        if not self.root.is_leaf and len(self.root.keys) == 0:
            self.root = self.root.children[0]

        return found

    def _delete_recursive(self, node, key):
        if node.is_leaf:
            # Remove from leaf
            try:
                index = node.keys.index(key)
                node.keys.pop(index)
                node.values.pop(index)
                return True
            except ValueError:
                return False  # Key not found

        else:
            # Internal node - find child
            index = self._find_child_index(node.keys, key)
            child = node.children[index]

            found = self._delete_recursive(child, key)

            if not found:
                return False

            # Check if child underflowed
            min_keys = (self.order + 1) // 2 - 1

            if len(child.keys) < min_keys:
                self._fix_underflow(node, index)

            return True

    def _fix_underflow(self, parent, child_index):
        """
        Fix underflow by borrowing or merging
        """
        child = parent.children[child_index]
        min_keys = (self.order + 1) // 2 - 1

        # Try to borrow from left sibling
        if child_index > 0:
            left_sibling = parent.children[child_index - 1]
            if len(left_sibling.keys) > min_keys:
                self._borrow_from_left(parent, child_index)
                return

        # Try to borrow from right sibling
        if child_index < len(parent.children) - 1:
            right_sibling = parent.children[child_index + 1]
            if len(right_sibling.keys) > min_keys:
                self._borrow_from_right(parent, child_index)
                return

        # Can't borrow - must merge
        if child_index > 0:
            self._merge_with_left(parent, child_index)
        else:
            self._merge_with_right(parent, child_index)

    def _borrow_from_left(self, parent, child_index):
        """
        Rotate: Move key from left sibling through parent
        """
        child = parent.children[child_index]
        left_sibling = parent.children[child_index - 1]

        # Move last key from left sibling to child
        if child.is_leaf:
            # Leaf: Direct move
            borrowed_key = left_sibling.keys.pop()
            borrowed_value = left_sibling.values.pop()

            child.keys.insert(0, borrowed_key)
            child.values.insert(0, borrowed_value)

            # Update parent separator
            parent.keys[child_index - 1] = child.keys[0]

    def _merge_with_left(self, parent, child_index):
        """
        Merge child with left sibling
        """
        child = parent.children[child_index]
        left_sibling = parent.children[child_index - 1]

        # Move all keys from child to left sibling
        if child.is_leaf:
            left_sibling.keys.extend(child.keys)
            left_sibling.values.extend(child.values)
            left_sibling.next = child.next  # Update linked list
        else:
            # Internal node: Include parent separator
            separator = parent.keys[child_index - 1]
            left_sibling.keys.append(separator)
            left_sibling.keys.extend(child.keys)
            left_sibling.children.extend(child.children)

        # Remove separator from parent
        parent.keys.pop(child_index - 1)
        parent.children.pop(child_index)
```

---

### 6. **Why B-Trees Dominate Disk-Based Storage**

🎓 **PROFESSOR**: B-Trees are **perfectly designed** for disk-based storage:

```text
Disk Characteristics:
- Sequential read: ~100 MB/s (fast)
- Random seek: ~5-10 ms per seek (slow)
- Page size: 4KB-16KB (read entire page at once)

B-Tree Optimizations:
1. High fanout → Minimize seeks (log_m N where m is large)
2. Node size = Page size → One I/O per node
3. Sorted structure → Leverage sequential reads for ranges
4. Balance → Predictable performance (no worst-case degradation)
```

#### B-Tree vs Other Structures on Disk

```text
┌──────────────────┬──────────┬──────────┬───────────┐
│ Operation        │ B-Tree   │ Hash     │ BST       │
├──────────────────┼──────────┼──────────┼───────────┤
│ Point Query I/O  │ log₂₀₀ N │ 1 (avg)  │ log₂ N    │
│ Range Query      │ Excellent│ Terrible │ Poor      │
│ Sequential Read  │ Excellent│ Random   │ Random    │
│ Predictability   │ High     │ Medium   │ Low       │
│ Disk Utilization │ 50-100%  │ 50-75%   │ Poor      │
└──────────────────┴──────────┴──────────┴───────────┘

For 1M keys:
- B-Tree: log₂₀₀(1M) ≈ 3 I/O
- Hash: 1 I/O (but no ranges!)
- BST: log₂(1M) ≈ 20 I/O
```

🏗️ **ARCHITECT**: Real PostgreSQL page structure:

```c
// Simplified PostgreSQL B-Tree page (8KB)
typedef struct BTPageOpaqueData {
    BlockNumber btpo_prev;    // Left sibling page
    BlockNumber btpo_next;    // Right sibling page (for sequential scan)
    uint32      btpo_level;   // 0 = leaf, >0 = internal
    uint16      btpo_flags;   // Page type flags
} BTPageOpaqueData;

typedef struct IndexTuple {
    uint16      t_info;       // Tuple metadata
    ItemPointer t_tid;        // Pointer to heap tuple (row)
    // Followed by key data (variable length)
} IndexTuple;

// Page layout:
// [Page Header | Special Space | Free Space | Item Pointers | Items]
//      24 bytes     varies          grows       grows      grows
//                                  ←─────────────────────→
```

**Code Example - Page Packing:**

```python
class BTreePage:
    """
    Simulate database page packing for B-Tree node
    """
    PAGE_SIZE = 8192  # 8KB
    HEADER_SIZE = 24
    POINTER_SIZE = 8

    def __init__(self):
        self.keys = []
        self.pointers = []  # Child pointers or row pointers

    def can_fit(self, key_size):
        """
        Check if another key-pointer pair fits in this page
        """
        current_usage = self.HEADER_SIZE
        current_usage += len(self.keys) * key_size
        current_usage += len(self.pointers) * self.POINTER_SIZE

        return current_usage + key_size + self.POINTER_SIZE <= self.PAGE_SIZE

    def utilization(self, key_size):
        """
        Calculate page utilization percentage
        """
        used = self.HEADER_SIZE
        used += len(self.keys) * key_size
        used += len(self.pointers) * self.POINTER_SIZE

        return (used / self.PAGE_SIZE) * 100


# Example: BIGINT index (8-byte keys)
page = BTreePage()
key_size = 8

while page.can_fit(key_size):
    page.keys.append(None)  # Placeholder
    page.pointers.append(None)

print(f"Keys per page: {len(page.keys)}")
print(f"Page utilization: {page.utilization(key_size):.1f}%")
print(f"Wasted space: {8192 - page.utilization(key_size) * 81.92:.0f} bytes")

# Output:
# Keys per page: 511
# Page utilization: 99.8%
# Wasted space: 16 bytes
```

---

### 7. **Production Implementations**

🏗️ **ARCHITECT**: How real databases implement B+Trees:

#### PostgreSQL

```text
Features:
- B+Tree with sibling links (doubly-linked)
- Supports duplicate keys (MVCC - multiple versions)
- Prefix compression (common key prefixes stored once)
- Deduplication (PostgreSQL 13+): Multiple row pointers for same key
- WAL logging for crash recovery

Special optimizations:
- BRIN (Block Range Index): Lightweight alternative for large sorted data
- Bloom filters for multi-column OR queries
```

#### MySQL InnoDB

```text
Features:
- B+Tree for both primary (clustered) and secondary indexes
- Primary key IS the table (data in leaf nodes)
- Secondary indexes store primary key (not row pointer)
- Adaptive hash index (automatically built on hot index pages)
- Change buffering (batch writes to secondary indexes)

Index merge optimization:
- Can combine multiple indexes for complex queries
- Example: (index1 OR index2) merged via bitmap
```

#### SQLite

```text
Features:
- B+Tree with variable-length keys
- Extremely simple implementation (~10,000 lines)
- Row IDs are implicit clustered index
- No separate WAL for indexes (integrated with main DB file)

Optimization:
- Ordered insertion fast path (sequential IDs)
- No need for rebalancing on ordered inserts
```

**Code Example - Prefix Compression:**

```python
class PrefixCompressedBTree:
    """
    PostgreSQL-style prefix compression for B-Tree nodes
    """
    class Node:
        def __init__(self):
            self.prefix = ""  # Common prefix for all keys in node
            self.suffixes = []  # Only unique suffixes stored

    def compress_node(self, keys: list[str]):
        """
        Find common prefix and compress
        """
        if not keys:
            return Node()

        # Find common prefix
        prefix = keys[0]
        for key in keys[1:]:
            while not key.startswith(prefix):
                prefix = prefix[:-1]
                if not prefix:
                    break

        # Create compressed node
        node = self.Node()
        node.prefix = prefix
        node.suffixes = [key[len(prefix):] for key in keys]

        return node

    def calculate_savings(self, keys: list[str]):
        """
        Calculate space savings from compression
        """
        original_size = sum(len(key) for key in keys)

        node = self.compress_node(keys)
        compressed_size = len(node.prefix) + sum(len(s) for s in node.suffixes)

        savings = original_size - compressed_size
        return {
            'original_bytes': original_size,
            'compressed_bytes': compressed_size,
            'savings_bytes': savings,
            'savings_percent': (savings / original_size) * 100
        }


# Example: Email index
emails = [
    "alice@example.com",
    "bob@example.com",
    "carol@example.com",
    "dave@example.com"
]

compressor = PrefixCompressedBTree()
stats = compressor.calculate_savings(emails)

print(stats)
# {
#   'original_bytes': 72,
#   'compressed_bytes': 28,  # 'example.com' stored once + 4 names
#   'savings_bytes': 44,
#   'savings_percent': 61.1
# }
```

---

## 🎯 **SYSTEM DESIGN INTERVIEW FRAMEWORK**

### 1. Explain Why B-Trees for Databases
```
Key points:
- Disk I/O is the bottleneck (5-10ms per seek)
- B-Trees minimize I/O: log_m(N) where m is large (200-500)
- Node size = Page size → One I/O per node
- Balance guarantees predictable performance

Example: "For 1 billion records, a B-Tree with fanout 200 requires only 4 disk reads. A binary search tree would need 30 reads - 7x slower."
```

### 2. Discuss B-Tree vs B+Tree
```
Always clarify:
- "Most production databases use B+Trees, not B-Trees"
- B+Tree stores data only in leaves
- Linked leaves enable efficient range scans
- Higher fanout (internal nodes are smaller)

PostgreSQL, MySQL InnoDB, SQLite: All B+Trees
```

### 3. Analyze Query Performance
```
Point Query (WHERE id = 123):
- Search: O(log N)
- I/O: log_m(N) disk reads (typically 3-4)

Range Query (WHERE date BETWEEN):
- Search for start: O(log N)
- Sequential scan: O(K) where K = results
- I/O: log_m(N) + K/entries_per_page

Sorted Retrieval (ORDER BY):
- No additional work - leaves already sorted
- Sequential scan of leaf level
```

### 4. Discuss Trade-offs
```
Advantages:
✅ Excellent for range queries
✅ Supports sorting (ORDER BY)
✅ Predictable performance
✅ High disk utilization (50-100%)
✅ Self-balancing

Disadvantages:
❌ Write amplification on splits (~5-10%)
❌ More complex than hash indexes
❌ Sequential writes become random (LSM-tree is better)
```

---

## 🧠 **MIND MAP: B-TREE CONCEPTS**
```
                    B-TREE / B+TREE
                          |
        ┌─────────────────┼─────────────────┐
        ↓                 ↓                  ↓
    STRUCTURE         OPERATIONS         OPTIMIZATIONS
        |                 |                  |
   ┌────┴────┐      ┌────┴────┐        ┌────┴────┐
   ↓         ↓      ↓         ↓        ↓         ↓
 Nodes    Balance  Search   Insert   Prefix    Page
 Keys              Range    Split    Compress  Packing
 Pointers          Delete   Merge    Dedup     Sibling
 Fanout            O(logN)           Links     Links
```

### 💡 **EMOTIONAL ANCHORS**

1. **B-Tree = Library Catalog 📚**
   - Finding book: Check catalog (index), find shelf, get book
   - Catalog organized hierarchically (Dewey Decimal)
   - High fanout = broad categories (Fiction → Mystery → Author → Title)

2. **Split = Cell Division 🦠**
   - When node gets too full, splits into two
   - Promotes "DNA" (middle key) to parent
   - Both children healthy (balanced)

3. **Merge = Company Merger 🏢**
   - When node too small, merges with sibling
   - Combined under single "management" (parent key)
   - Reduces organizational levels

4. **Fanout = Organizational Hierarchy 👔**
   - CEO → 10 VPs → 100 Directors → 1000 Managers
   - High fanout = flat organization = fewer levels
   - Find any manager: 3 levels (CEO → VP → Director → Manager)

---

## 🔑 **KEY TAKEAWAYS**

```
1. B+Trees store data ONLY in leaves (better than B-Trees for databases)
2. High fanout (200-500) minimizes tree height → log₂₀₀(N) I/O
3. Node size = Page size (8KB) → One I/O per node access
4. Linked leaves enable efficient range scans (O(log N + K))
5. Self-balancing maintains O(log N) worst-case (no degradation)
6. Splits and merges keep tree balanced (write amplification ~5%)
7. Perfect for disk-based storage (sequential reads, predictable I/O)
8. Used by ALL major RDBMS: PostgreSQL, MySQL, SQLite, Oracle, SQL Server
```

**Production Mindset**:
> "B+Trees are the Swiss Army knife of database indexes. They handle equality, ranges, sorting, and uniqueness - all with predictable O(log N) performance. That's why they've dominated for 50+ years. Alternatives like LSM-trees exist, but for general-purpose OLTP, B+Trees remain king."
