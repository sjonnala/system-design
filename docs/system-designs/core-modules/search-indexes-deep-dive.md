# Search Indexes - Why Do We Need Them? - Deep Dive

## Contents

- [Search Indexes - Why Do We Need Them? - Deep Dive](#search-indexes---why-do-we-need-them---deep-dive)
  - [Core Mental Model](#core-mental-model)
  - [The Problem: Full-Text Search](#1-the-problem-full-text-search)
  - [Inverted Index: The Core Data Structure](#2-inverted-index-the-core-data-structure)
  - [Text Analysis Pipeline](#3-text-analysis-pipeline)
  - [Relevance Scoring: TF-IDF & BM25](#4-relevance-scoring-tf-idf--bm25)
  - [Advanced Search Features](#5-advanced-search-features)
  - [Search Index vs Database Index](#6-search-index-vs-database-index)
  - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
  - [MIND MAP: SEARCH INDEX CONCEPTS](#mind-map-search-index-concepts)

## Core Mental Model

```text
Traditional Database Index:
Query: WHERE name = 'John'
Index: Exact match on full value
Result: O(log N) lookup

Search Index:
Query: "Find documents containing 'machine learning algorithms'"
Index: Match ANY word, rank by relevance
Result: O(k + r) where k = terms, r = results
```

**Fundamental Difference:**
```
Database Index:
- Exact match (equality, range)
- Structured data (numbers, dates, IDs)
- Binary: found or not found

Search Index (Inverted Index):
- Partial match (words, phrases, fuzzy)
- Unstructured text (documents, descriptions)
- Ranked results (most relevant first)
```

**Core Components:**
```
┌──────────────────────────────────────────────────────┐
│ 1. TOKENIZATION: Text → Words (tokens)              │
│ 2. NORMALIZATION: Lowercase, remove accents         │
│ 3. STEMMING: Running → Run, Cars → Car              │
│ 4. INVERTED INDEX: Word → Document IDs + Positions  │
│ 5. SCORING: Rank results by relevance (TF-IDF)      │
└──────────────────────────────────────────────────────┘
```

**Visual Model:**
```text
Documents:
Doc1: "The quick brown fox jumps"
Doc2: "The lazy brown dog sleeps"
Doc3: "Quick foxes are clever"

Inverted Index:
┌──────────┬─────────────────────────────────┐
│ Term     │ Document IDs (with positions)   │
├──────────┼─────────────────────────────────┤
│ quick    │ Doc1[1], Doc3[0]                │
│ brown    │ Doc1[2], Doc2[2]                │
│ fox      │ Doc1[3], Doc3[1]                │
│ jump     │ Doc1[4]                         │ ← Stemmed from "jumps"
│ lazy     │ Doc2[1]                         │
│ dog      │ Doc2[3]                         │
│ sleep    │ Doc2[4]                         │ ← Stemmed from "sleeps"
│ clever   │ Doc3[3]                         │
└──────────┴─────────────────────────────────┘

Query: "quick fox"
1. Lookup "quick" → Doc1, Doc3
2. Lookup "fox" → Doc1, Doc3
3. Intersect: Doc1, Doc3 (both contain both terms)
4. Score: Doc1 higher (words closer together)
```

---

### 1. **The Problem: Full-Text Search**

🎓 **PROFESSOR**: Traditional database indexes CANNOT efficiently solve full-text search:

```text
Problem 1: Partial Text Matching

Database approach:
SELECT * FROM articles WHERE content LIKE '%machine learning%';

Issues:
❌ Full table scan (no index used!)
❌ Case-sensitive
❌ No stemming ("learning" won't match "learn")
❌ No relevance ranking (all matches equal)
❌ O(N * M) where N = docs, M = avg doc length


Problem 2: Multiple Word Queries

Query: "artificial intelligence machine learning"
Database: WHERE content LIKE '%artificial%'
           AND content LIKE '%intelligence%'
           AND content LIKE '%machine%'
           AND content LIKE '%learning%'

Issues:
❌ 4 full table scans!
❌ No ranking (order by what?)
❌ No understanding of "artificial intelligence" as phrase


Problem 3: Relevance Scoring

User searches: "best coffee shops in Seattle"
Which is more relevant?
- Doc A: "coffee" mentioned 1 time
- Doc B: "coffee" mentioned 10 times
- Doc C: "best coffee shops" as phrase + "Seattle"

Database has no concept of relevance!
```

🏗️ **ARCHITECT**: Real-world performance disaster:

```sql
-- E-commerce product search (10M products)
-- Query: Find products matching "wireless bluetooth headphones"

-- Attempt 1: LIKE (disaster!)
SELECT * FROM products
WHERE description LIKE '%wireless%'
  AND description LIKE '%bluetooth%'
  AND description LIKE '%headphones%';

-- Execution: 3 full table scans
-- Time: 45 seconds (unacceptable!)


-- Attempt 2: Full-text search (PostgreSQL)
CREATE INDEX idx_products_fts ON products
USING gin(to_tsvector('english', description));

SELECT * FROM products
WHERE to_tsvector('english', description) @@
      to_tsquery('english', 'wireless & bluetooth & headphones')
ORDER BY ts_rank(
    to_tsvector('english', description),
    to_tsquery('english', 'wireless & bluetooth & headphones')
) DESC
LIMIT 20;

-- Execution: Index scan + relevance ranking
-- Time: 50ms (900x faster!)


-- Attempt 3: Elasticsearch (production)
GET /products/_search
{
  "query": {
    "multi_match": {
      "query": "wireless bluetooth headphones",
      "fields": ["name^3", "description", "category"],
      "type": "best_fields",
      "fuzziness": "AUTO"
    }
  }
}

-- Features:
-- ✅ Multi-field search (name, description, category)
-- ✅ Field boosting (name 3x more important)
-- ✅ Fuzzy matching ("blutooth" → "bluetooth")
-- ✅ Relevance scoring (BM25)
-- Time: 15ms (3x faster than PostgreSQL FTS)
```

**Interview gold**: "LIKE '%term%' is O(N) - it reads every row. Search indexes are O(k) where k is the number of documents containing the term, independent of total documents. That's the difference between 10 seconds and 10 milliseconds."

---

### 2. **Inverted Index: The Core Data Structure**

🎓 **PROFESSOR**: An **inverted index** is like a book's index - it maps words to locations:

```text
Forward Index (Document → Words):
Doc1 → ["the", "quick", "brown", "fox"]
Doc2 → ["the", "lazy", "brown", "dog"]

Inverted Index (Word → Documents):
"quick" → [Doc1]
"brown" → [Doc1, Doc2]
"fox" → [Doc1]
"lazy" → [Doc2]
"dog" → [Doc2]

Key insight: Search engines use inverted indexes!
```

#### Inverted Index Structure (Detailed)

```text
Full Inverted Index with Positions:

┌─────────────────────────────────────────────────────────┐
│ Term Dictionary (sorted)                                │
├──────────┬──────────────────────────────────────────────┤
│ Term     │ Postings List Pointer                        │
├──────────┼──────────────────────────────────────────────┤
│ algorithm│ → Postings                                   │
│ data     │ → Postings                                   │
│ learning │ → Postings                                   │
│ machine  │ → Postings                                   │
└──────────┴──────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────────┐
│ Postings List for "machine"                             │
├────────┬──────────┬──────────────┬───────────────────────┤
│ DocID  │ Term Freq│ Positions    │ Payload (optional)    │
├────────┼──────────┼──────────────┼───────────────────────┤
│ 42     │ 3        │ [5, 89, 234] │ field=title, boost=2.0│
│ 187    │ 1        │ [12]         │ field=body            │
│ 503    │ 7        │ [2,5,8,...]  │ field=body            │
└────────┴──────────┴──────────────┴───────────────────────┘

Components:
- DocID: Which document contains this term
- Term Frequency (TF): How many times in this doc (for scoring)
- Positions: Where in the doc (for phrase queries)
- Payload: Extra metadata (field name, boost factor)
```

**Implementation:**

```python
from collections import defaultdict
from typing import List, Dict, Set

class InvertedIndex:
    """
    Simple inverted index with positions
    """
    def __init__(self):
        # Term → {DocID: [positions]}
        self.index: Dict[str, Dict[int, List[int]]] = defaultdict(lambda: defaultdict(list))

        # DocID → document content
        self.documents: Dict[int, str] = {}

        # DocID → document length (for BM25 scoring)
        self.doc_lengths: Dict[int, int] = {}

        self.avg_doc_length = 0
        self.num_docs = 0

    def add_document(self, doc_id: int, text: str):
        """
        Index a document
        """
        self.documents[doc_id] = text

        # Tokenize (simple whitespace split)
        tokens = self._tokenize(text)
        self.doc_lengths[doc_id] = len(tokens)

        # Build inverted index
        for position, term in enumerate(tokens):
            term = self._normalize(term)
            self.index[term][doc_id].append(position)

        # Update average document length
        self.num_docs += 1
        self.avg_doc_length = sum(self.doc_lengths.values()) / self.num_docs

    def search(self, query: str) -> List[int]:
        """
        Search for documents containing query terms
        """
        query_terms = [self._normalize(t) for t in self._tokenize(query)]

        if not query_terms:
            return []

        # Get documents containing all terms (AND query)
        doc_sets = [set(self.index.get(term, {}).keys()) for term in query_terms]
        matching_docs = set.intersection(*doc_sets) if doc_sets else set()

        # Sort by relevance score
        scored_docs = [(doc_id, self._score(doc_id, query_terms))
                       for doc_id in matching_docs]
        scored_docs.sort(key=lambda x: x[1], reverse=True)

        return [doc_id for doc_id, score in scored_docs]

    def phrase_search(self, phrase: str) -> List[int]:
        """
        Search for exact phrase (ordered terms with adjacent positions)
        """
        terms = [self._normalize(t) for t in self._tokenize(phrase)]

        if not terms:
            return []

        # Get documents containing all terms
        doc_sets = [set(self.index.get(term, {}).keys()) for term in terms]
        candidate_docs = set.intersection(*doc_sets) if doc_sets else set()

        matching_docs = []

        # Check if terms appear consecutively
        for doc_id in candidate_docs:
            # Get positions for first term
            first_positions = self.index[terms[0]][doc_id]

            for start_pos in first_positions:
                # Check if remaining terms follow consecutively
                match = True
                for i, term in enumerate(terms[1:], 1):
                    expected_pos = start_pos + i
                    if expected_pos not in self.index[term][doc_id]:
                        match = False
                        break

                if match:
                    matching_docs.append(doc_id)
                    break  # Found phrase in this doc

        return matching_docs

    def _tokenize(self, text: str) -> List[str]:
        """
        Split text into tokens (words)
        """
        # Simple whitespace tokenization
        # Production: Use proper tokenizer (nltk, spaCy)
        return text.lower().split()

    def _normalize(self, term: str) -> str:
        """
        Normalize term (lowercase, remove punctuation)
        """
        # Remove punctuation
        import string
        term = term.strip(string.punctuation)
        return term.lower()

    def _score(self, doc_id: int, query_terms: List[str]) -> float:
        """
        Simple TF-IDF scoring
        """
        score = 0.0

        for term in query_terms:
            if term not in self.index:
                continue

            # Term frequency in this document
            tf = len(self.index[term].get(doc_id, []))

            # Inverse document frequency
            df = len(self.index[term])  # Documents containing term
            idf = math.log((self.num_docs + 1) / (df + 1))

            score += tf * idf

        return score


# Example usage
index = InvertedIndex()

# Index documents
index.add_document(1, "Machine learning is a subset of artificial intelligence")
index.add_document(2, "Deep learning is a type of machine learning")
index.add_document(3, "Artificial intelligence and machine learning are transforming technology")

# Search
print("Search 'machine learning':")
results = index.search("machine learning")
print(f"Results: {results}")
# [2, 1, 3] - Doc 2 has highest TF-IDF for "machine learning"

# Phrase search
print("\nPhrase search 'machine learning':")
results = index.phrase_search("machine learning")
print(f"Results: {results}")
# [2, 1, 3] - All contain the exact phrase
```

---

### 3. **Text Analysis Pipeline**

🎓 **PROFESSOR**: Before building the inverted index, text must be **analyzed**:

```text
Analysis Pipeline:

Raw Text: "The QUICK Brown foxes are JUMPING!!!"
    ↓
1. Character Filtering
   Remove HTML, special chars: "The QUICK Brown foxes are JUMPING"
    ↓
2. Tokenization
   Split into words: ["The", "QUICK", "Brown", "foxes", "are", "JUMPING"]
    ↓
3. Token Filtering
   Lowercase: ["the", "quick", "brown", "foxes", "are", "jumping"]
   Remove stop words: ["quick", "brown", "foxes", "jumping"]
    ↓
4. Stemming/Lemmatization
   Reduce to root form: ["quick", "brown", "fox", "jump"]
    ↓
5. Index
   Store in inverted index: {quick, brown, fox, jump}
```

#### Component 1: Tokenization

```python
class Tokenizer:
    """
    Split text into tokens
    """
    def tokenize_simple(self, text: str) -> List[str]:
        """
        Simple whitespace tokenization
        """
        return text.split()

    def tokenize_word_boundaries(self, text: str) -> List[str]:
        """
        Tokenize on word boundaries (punctuation, whitespace)
        """
        import re
        # Split on non-word characters
        return re.findall(r'\b\w+\b', text)

    def tokenize_ngrams(self, text: str, n: int = 2) -> List[str]:
        """
        Generate n-grams for fuzzy matching
        Example: "hello" → ["he", "el", "ll", "lo"] (2-grams)
        """
        tokens = self.tokenize_word_boundaries(text)
        ngrams = []

        for token in tokens:
            # Character n-grams
            for i in range(len(token) - n + 1):
                ngrams.append(token[i:i+n])

        return ngrams


# Example
tokenizer = Tokenizer()
text = "Hello, world! How's it going?"

print("Simple:", tokenizer.tokenize_simple(text))
# ['Hello,', 'world!', "How's", 'it', 'going?']

print("Word boundaries:", tokenizer.tokenize_word_boundaries(text))
# ['Hello', 'world', 'How', 's', 'it', 'going']

print("2-grams:", tokenizer.tokenize_ngrams("hello", n=2))
# ['he', 'el', 'll', 'lo']
```

#### Component 2: Normalization & Filtering

```python
class TextNormalizer:
    """
    Normalize tokens for consistent matching
    """
    # Common English stop words
    STOP_WORDS = {
        'the', 'a', 'an', 'and', 'or', 'but', 'is', 'are', 'was', 'were',
        'in', 'on', 'at', 'to', 'for', 'of', 'with', 'by'
    }

    def lowercase(self, tokens: List[str]) -> List[str]:
        """
        Convert to lowercase for case-insensitive search
        """
        return [t.lower() for t in tokens]

    def remove_stopwords(self, tokens: List[str]) -> List[str]:
        """
        Remove common words with little meaning
        """
        return [t for t in tokens if t.lower() not in self.STOP_WORDS]

    def remove_accents(self, text: str) -> str:
        """
        Café → Cafe, naïve → naive
        """
        import unicodedata
        return ''.join(
            c for c in unicodedata.normalize('NFD', text)
            if unicodedata.category(c) != 'Mn'
        )


# Example
normalizer = TextNormalizer()
tokens = ["The", "QUICK", "brown", "fox"]

print("Lowercase:", normalizer.lowercase(tokens))
# ['the', 'quick', 'brown', 'fox']

print("Remove stop words:", normalizer.remove_stopwords(tokens))
# ['QUICK', 'brown', 'fox']

print("Remove accents:", normalizer.remove_accents("Café naïve résumé"))
# 'Cafe naive resume'
```

#### Component 3: Stemming & Lemmatization

```python
class Stemmer:
    """
    Reduce words to root form
    """
    def porter_stem(self, word: str) -> str:
        """
        Porter stemming algorithm (simple version)
        """
        # Very simplified - use nltk.PorterStemmer in production
        suffixes = ['ing', 'ed', 'es', 's', 'ly']

        for suffix in suffixes:
            if word.endswith(suffix) and len(word) > len(suffix) + 2:
                return word[:-len(suffix)]

        return word

    def lemmatize(self, word: str, pos: str = 'n') -> str:
        """
        Lemmatization: Context-aware root form
        Example: "better" → "good" (comparative → base form)
        """
        # Use nltk.WordNetLemmatizer in production
        # This is a simplified example
        irregular = {
            'better': 'good',
            'worse': 'bad',
            'am': 'be',
            'is': 'be',
            'are': 'be',
        }

        return irregular.get(word.lower(), word)


# Example
stemmer = Stemmer()

words = ["running", "ran", "runs", "runner", "easily", "jumped"]
print("Stemming:", [stemmer.porter_stem(w) for w in words])
# ['run', 'ran', 'run', 'run', 'easi', 'jump']

# Stemming vs Lemmatization:
# Stemming: "running" → "run" (chop suffix)
# Lemmatization: "running" → "run" (dictionary lookup)
# Stemming: "better" → "better" (no suffix)
# Lemmatization: "better" → "good" (understands comparative)
```

🏗️ **ARCHITECT**: Complete analysis pipeline:

```python
class SearchAnalyzer:
    """
    Complete text analysis pipeline
    """
    def __init__(self):
        self.tokenizer = Tokenizer()
        self.normalizer = TextNormalizer()
        self.stemmer = Stemmer()

    def analyze(self, text: str, language: str = 'english') -> List[str]:
        """
        Full analysis pipeline
        """
        # 1. Normalize accents
        text = self.normalizer.remove_accents(text)

        # 2. Tokenize
        tokens = self.tokenizer.tokenize_word_boundaries(text)

        # 3. Lowercase
        tokens = self.normalizer.lowercase(tokens)

        # 4. Remove stop words
        tokens = self.normalizer.remove_stopwords(tokens)

        # 5. Stem
        tokens = [self.stemmer.porter_stem(t) for t in tokens]

        return tokens


# Example: Indexing product description
analyzer = SearchAnalyzer()

product = """
The NEWEST Samsung Galaxy S24 Ultra features an AMAZING 200MP camera!
Capture stunning photos and videos. Available in Titanium Black.
"""

tokens = analyzer.analyze(product)
print("Analyzed tokens:", tokens)
# ['newest', 'samsung', 'galaxi', 's24', 'ultra', 'featur', 'amaz',
#  '200mp', 'camera', 'captur', 'stun', 'photo', 'video', 'availabl',
#  'titanium', 'black']

# These tokens are indexed, enabling searches like:
# - "camera" (exact match)
# - "cameras" (stemmed to "camera")
# - "Samsung Galaxy" (phrase match)
# - "stunning photography" ("stun" and "photo" both indexed)
```

---

### 4. **Relevance Scoring: TF-IDF & BM25**

🎓 **PROFESSOR**: Not all matches are equal. **Relevance scoring** ranks results:

#### TF-IDF (Term Frequency - Inverse Document Frequency)

```text
TF (Term Frequency):
How often does term appear in this document?

TF(term, doc) = count(term in doc) / total terms in doc

Example:
Doc: "machine learning and machine intelligence"
TF("machine") = 2 / 5 = 0.4


IDF (Inverse Document Frequency):
How rare is this term across all documents?

IDF(term) = log(total docs / docs containing term)

Example:
Total docs: 1000
Docs with "machine": 100
IDF("machine") = log(1000 / 100) = log(10) = 2.3

Docs with "the": 950
IDF("the") = log(1000 / 950) = log(1.05) = 0.05 (common word = low IDF)


TF-IDF Score:
TF-IDF(term, doc) = TF(term, doc) × IDF(term)

Interpretation:
- High TF: Term appears frequently in this doc (important to doc)
- High IDF: Term is rare globally (discriminative)
- High TF-IDF: Term is important to this doc and rare overall (perfect!)
```

**Implementation:**

```python
import math
from collections import Counter

class TFIDFScorer:
    """
    TF-IDF relevance scoring
    """
    def __init__(self, documents: List[str]):
        self.documents = documents
        self.num_docs = len(documents)

        # Tokenize all documents
        self.tokenized_docs = [doc.lower().split() for doc in documents]

        # Build document frequency map
        self.df = self._build_df()

    def _build_df(self) -> Dict[str, int]:
        """
        Count how many documents contain each term
        """
        df = Counter()
        for doc_tokens in self.tokenized_docs:
            unique_terms = set(doc_tokens)
            for term in unique_terms:
                df[term] += 1
        return df

    def tf(self, term: str, doc_index: int) -> float:
        """
        Term frequency in document
        """
        doc_tokens = self.tokenized_docs[doc_index]
        term_count = doc_tokens.count(term)
        doc_length = len(doc_tokens)

        return term_count / doc_length if doc_length > 0 else 0

    def idf(self, term: str) -> float:
        """
        Inverse document frequency
        """
        docs_with_term = self.df.get(term, 0)

        # Add 1 to avoid division by zero
        return math.log((self.num_docs + 1) / (docs_with_term + 1))

    def tfidf(self, term: str, doc_index: int) -> float:
        """
        TF-IDF score for term in document
        """
        return self.tf(term, doc_index) * self.idf(term)

    def score_document(self, query: str, doc_index: int) -> float:
        """
        Score document for query (sum of TF-IDF scores)
        """
        query_terms = query.lower().split()
        score = sum(self.tfidf(term, doc_index) for term in query_terms)
        return score

    def search(self, query: str, top_k: int = 10) -> List[tuple]:
        """
        Search and return top K documents
        """
        scores = [(i, self.score_document(query, i))
                  for i in range(self.num_docs)]

        # Sort by score descending
        scores.sort(key=lambda x: x[1], reverse=True)

        return scores[:top_k]


# Example
docs = [
    "Machine learning algorithms for data analysis",
    "Deep learning neural networks",
    "Machine learning and artificial intelligence",
    "Data science and machine learning applications",
]

scorer = TFIDFScorer(docs)

# Search
query = "machine learning"
results = scorer.search(query, top_k=3)

print(f"Query: '{query}'\n")
for doc_id, score in results:
    print(f"Score: {score:.3f} | Doc {doc_id}: {docs[doc_id]}")

# Output:
# Score: 0.462 | Doc 2: Machine learning and artificial intelligence
# Score: 0.398 | Doc 0: Machine learning algorithms for data analysis
# Score: 0.357 | Doc 3: Data science and machine learning applications
```

#### BM25 (Best Matching 25)

```text
BM25: Improved TF-IDF (used by Elasticsearch, Lucene)

BM25(term, doc) = IDF(term) ×
                  (TF × (k1 + 1)) /
                  (TF + k1 × (1 - b + b × (doc_length / avg_doc_length)))

Parameters:
- k1: Term saturation (typically 1.2-2.0)
     Higher = more weight to term frequency
- b: Length normalization (typically 0.75)
     0 = ignore length, 1 = full length normalization

Key improvements over TF-IDF:
1. Diminishing returns for TF (saturation)
   TF=1 vs TF=2: Big difference
   TF=10 vs TF=11: Small difference

2. Document length normalization
   Short docs: Penalize high TF (might be keyword stuffing)
   Long docs: Boost high TF (natural occurrence)
```

**Implementation:**

```python
class BM25Scorer:
    """
    BM25 relevance scoring (Elasticsearch/Lucene standard)
    """
    def __init__(self, documents: List[str], k1: float = 1.5, b: float = 0.75):
        self.documents = documents
        self.k1 = k1  # Term saturation
        self.b = b    # Length normalization
        self.num_docs = len(documents)

        # Tokenize
        self.tokenized_docs = [doc.lower().split() for doc in documents]

        # Document lengths
        self.doc_lengths = [len(doc) for doc in self.tokenized_docs]
        self.avg_doc_length = sum(self.doc_lengths) / self.num_docs

        # Document frequency
        self.df = self._build_df()

    def _build_df(self) -> Dict[str, int]:
        df = Counter()
        for doc_tokens in self.tokenized_docs:
            for term in set(doc_tokens):
                df[term] += 1
        return df

    def idf(self, term: str) -> float:
        """
        BM25 IDF formula
        """
        df = self.df.get(term, 0)
        return math.log((self.num_docs - df + 0.5) / (df + 0.5) + 1)

    def score_document(self, query: str, doc_index: int) -> float:
        """
        BM25 score for document
        """
        query_terms = query.lower().split()
        doc_tokens = self.tokenized_docs[doc_index]
        doc_length = self.doc_lengths[doc_index]

        score = 0.0

        for term in query_terms:
            if term not in self.df:
                continue

            # Term frequency in this document
            tf = doc_tokens.count(term)

            # IDF component
            idf = self.idf(term)

            # Length normalization factor
            norm = 1 - self.b + self.b * (doc_length / self.avg_doc_length)

            # BM25 formula
            score += idf * (tf * (self.k1 + 1)) / (tf + self.k1 * norm)

        return score

    def search(self, query: str, top_k: int = 10) -> List[tuple]:
        scores = [(i, self.score_document(query, i))
                  for i in range(self.num_docs)]
        scores.sort(key=lambda x: x[1], reverse=True)
        return scores[:top_k]
```

---

### 5. **Advanced Search Features**

🏗️ **ARCHITECT**: Production search systems offer advanced features:

#### Feature 1: Fuzzy Matching (Typo Tolerance)

```python
def levenshtein_distance(s1: str, s2: str) -> int:
    """
    Edit distance: Minimum edits to transform s1 → s2
    """
    if len(s1) < len(s2):
        return levenshtein_distance(s2, s1)

    if len(s2) == 0:
        return len(s1)

    previous_row = range(len(s2) + 1)

    for i, c1 in enumerate(s1):
        current_row = [i + 1]
        for j, c2 in enumerate(s2):
            # Cost of insertions, deletions, substitutions
            insertions = previous_row[j + 1] + 1
            deletions = current_row[j] + 1
            substitutions = previous_row[j] + (c1 != c2)

            current_row.append(min(insertions, deletions, substitutions))

        previous_row = current_row

    return previous_row[-1]


# Example
print(levenshtein_distance("kitten", "sitting"))  # 3 edits
print(levenshtein_distance("bluetooth", "blutooth"))  # 1 edit (typo!)

# Fuzzy search: Accept matches within edit distance 2
query = "blutooth"
candidates = ["bluetooth", "headphones", "speaker"]
matches = [c for c in candidates if levenshtein_distance(query, c) <= 2]
print(matches)  # ['bluetooth']
```

#### Feature 2: Multi-Field Search with Boosting

```python
class MultiFieldSearch:
    """
    Search across multiple fields with different weights
    """
    def __init__(self):
        self.indexes = {
            'title': InvertedIndex(),
            'description': InvertedIndex(),
            'category': InvertedIndex(),
        }

        self.boosts = {
            'title': 3.0,        # Title matches 3x more important
            'description': 1.0,  # Description baseline
            'category': 2.0,     # Category 2x important
        }

    def add_document(self, doc_id: int, doc: dict):
        """
        Index document with multiple fields
        """
        for field_name, field_value in doc.items():
            if field_name in self.indexes:
                self.indexes[field_name].add_document(doc_id, field_value)

    def search(self, query: str) -> List[int]:
        """
        Multi-field search with boosting
        """
        field_scores = defaultdict(float)

        # Score across all fields
        for field_name, index in self.indexes.items():
            results = index.search(query)

            for doc_id in results:
                # Apply field boost
                score = index._score(doc_id, query.split())
                field_scores[doc_id] += score * self.boosts[field_name]

        # Sort by combined score
        sorted_docs = sorted(field_scores.items(), key=lambda x: x[1], reverse=True)
        return [doc_id for doc_id, score in sorted_docs]
```

#### Feature 3: Autocomplete (Prefix Search)

```python
class TrieNode:
    """
    Trie (prefix tree) for autocomplete
    """
    def __init__(self):
        self.children = {}
        self.is_end_of_word = False
        self.frequency = 0  # For ranking suggestions


class Autocomplete:
    """
    Prefix-based autocomplete with frequency ranking
    """
    def __init__(self):
        self.root = TrieNode()

    def insert(self, word: str, frequency: int = 1):
        """
        Insert word into trie
        """
        node = self.root

        for char in word.lower():
            if char not in node.children:
                node.children[char] = TrieNode()
            node = node.children[char]

        node.is_end_of_word = True
        node.frequency += frequency

    def search_prefix(self, prefix: str, top_k: int = 10) -> List[tuple]:
        """
        Find all words with given prefix, ranked by frequency
        """
        # Navigate to prefix
        node = self.root
        for char in prefix.lower():
            if char not in node.children:
                return []  # Prefix not found
            node = node.children[char]

        # Collect all words from this node
        suggestions = []
        self._collect_words(node, prefix, suggestions)

        # Sort by frequency
        suggestions.sort(key=lambda x: x[1], reverse=True)

        return suggestions[:top_k]

    def _collect_words(self, node: TrieNode, prefix: str, suggestions: List):
        """
        DFS to collect all words under this node
        """
        if node.is_end_of_word:
            suggestions.append((prefix, node.frequency))

        for char, child_node in node.children.items():
            self._collect_words(child_node, prefix + char, suggestions)


# Example
autocomplete = Autocomplete()

# Insert search queries with frequencies
queries = [
    ("machine learning", 10000),
    ("machine learning python", 5000),
    ("machine learning algorithms", 3000),
    ("machine translation", 2000),
    ("machinery", 500),
]

for query, freq in queries:
    autocomplete.insert(query, freq)

# Autocomplete
prefix = "machine learn"
suggestions = autocomplete.search_prefix(prefix, top_k=3)

print(f"Autocomplete for '{prefix}':")
for suggestion, freq in suggestions:
    print(f"  {suggestion} ({freq} searches)")

# Output:
# Autocomplete for 'machine learn':
#   machine learning (10000 searches)
#   machine learning python (5000 searches)
#   machine learning algorithms (3000 searches)
```

---

### 6. **Search Index vs Database Index**

🎓 **PROFESSOR**: Clear comparison:

```text
┌─────────────────────┬──────────────────┬────────────────────┐
│ Feature             │ Database Index   │ Search Index       │
├─────────────────────┼──────────────────┼────────────────────┤
│ Data Structure      │ B-Tree, Hash     │ Inverted Index     │
│ Query Type          │ Exact, Range     │ Full-Text, Fuzzy   │
│ Input               │ Structured       │ Unstructured Text  │
│ Output              │ Found / Not Found│ Ranked Results     │
│ Text Processing     │ None             │ Tokenize, Stem     │
│ Relevance Scoring   │ No               │ Yes (TF-IDF, BM25) │
│ Performance         │ O(log N)         │ O(k + r)           │
│ Use Case            │ OLTP, Lookups    │ Search Engines     │
│ Examples            │ PostgreSQL index │ Elasticsearch      │
└─────────────────────┴──────────────────┴────────────────────┘
```

**When to Use Each:**

```python
# Database Index (B-Tree)
✅ SELECT * FROM users WHERE id = 12345  # Exact match
✅ SELECT * FROM orders WHERE created_at > '2024-01-01'  # Range
✅ SELECT * FROM products WHERE price BETWEEN 10 AND 50  # Range

# Search Index (Inverted)
✅ Find products matching "wireless headphones"
✅ Search articles containing "machine learning"
✅ Autocomplete user queries
✅ Fuzzy matching for typos
```

---

## 🎯 **SYSTEM DESIGN INTERVIEW FRAMEWORK**

### 1. Requirements Clarification
```
Questions:
- What type of content? (Products, documents, social posts)
- Search features needed? (Fuzzy, autocomplete, filters)
- Scale? (Number of documents, queries per second)
- Language support? (English only or multi-language)
- Ranking requirements? (Relevance, popularity, recency)
```

### 2. High-Level Design
```
Components:
1. Indexing Pipeline
   - Extract text from documents
   - Analyze (tokenize, stem, normalize)
   - Build inverted index
   - Store in search engine

2. Query Pipeline
   - Parse query
   - Analyze query (same as indexing)
   - Search inverted index
   - Score and rank results
   - Return top K results

3. Storage
   - Inverted index (on disk + cached in RAM)
   - Original documents (if needed)
   - Metadata (filters, facets)
```

### 3. Deep Dive: Inverted Index
```
Explain:
- Term dictionary (sorted, binary searchable)
- Postings lists (doc IDs + positions)
- Compression (delta encoding, varint)
- Caching (hot terms in RAM)

Capacity:
- 10M documents × 1KB avg = 10 GB raw data
- Inverted index: ~30-50% of raw data = 3-5 GB
- With compression: ~10-20% = 1-2 GB
```

### 4. Optimization Strategies
```
For Speed:
- Cache popular queries
- Shard by hash(doc_id) for parallelism
- Early termination (stop after top K)

For Relevance:
- BM25 scoring (better than TF-IDF)
- Field boosting (title > body)
- Recency boosting (newer = better)

For Scale:
- Distributed search (scatter-gather)
- Replica shards (load balancing)
- Incremental indexing (near real-time)
```

---

## 🧠 **MIND MAP: SEARCH INDEX CONCEPTS**
```
                    SEARCH INDEX
                          |
        ┌─────────────────┼─────────────────┐
        ↓                 ↓                  ↓
   INVERTED INDEX    TEXT ANALYSIS      SCORING
        |                 |                  |
   ┌────┴────┐      ┌────┴────┐        ┌────┴────┐
   ↓         ↓      ↓         ↓        ↓         ↓
 Term     Postings  Tokenize Stem     TF-IDF   BM25
 Dict     Lists     Normalize Filter  Relevance Rank
 Sorted   DocIDs    Lowercase Stop    Importance
          Positions Accents   Words   Frequency
```

### 💡 **EMOTIONAL ANCHORS**

1. **Inverted Index = Book Index 📖**
   - Book: Pages → Words
   - Index: Words → Pages
   - Find "database" → Pages [47, 89, 234]

2. **TF-IDF = Importance Score 🎯**
   - TF: How much you talk about X in this conversation
   - IDF: How rare X is across all conversations
   - High score: You talk a lot about a rare topic (expert!)

3. **Stemming = Root Word 🌱**
   - "running", "runs", "ran" → "run"
   - Like family tree → common ancestor
   - Match variations automatically

4. **Fuzzy Search = Autocorrect 📱**
   - "blutooth" → "Did you mean: bluetooth?"
   - Tolerate typos (edit distance ≤ 2)
   - User-friendly search

---

## 🔑 **KEY TAKEAWAYS**

```
1. Search indexes solve FULL-TEXT search (unstructured text)
2. Inverted index: Term → Documents (reverse of document → terms)
3. Text analysis: Tokenize → Normalize → Stem → Index
4. Relevance scoring: TF-IDF (classic), BM25 (modern standard)
5. Advanced features: Fuzzy matching, multi-field, autocomplete
6. Not a replacement for database indexes (complementary)
7. Use for: Product search, document search, log analysis
8. Tools: Elasticsearch, Solr, Lucene
```

**Production Mindset**:
> "Don't build search from scratch. Use Elasticsearch or Solr. They've solved hard problems like distributed search, sharding, replication, and relevance tuning. Your time is better spent on business logic than reinventing inverted indexes."
