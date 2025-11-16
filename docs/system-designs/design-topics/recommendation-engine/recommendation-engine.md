# Design a Recommendation Engine

**Companies**: Netflix, Amazon, Spotify, YouTube, TikTok, LinkedIn
**Difficulty**: Advanced
**Time**: 60-90 minutes

## Problem Statement

Design a personalized recommendation system that suggests relevant items (products, videos, music, content) to users based on their behavior, preferences, and contextual signals. The system should handle billions of users and items while providing real-time, personalized recommendations.

---

## Step 1: Requirements (10 min)

### Functional Requirements

1. **Personalized Recommendations**: Generate top-N recommendations for each user
2. **Real-time Updates**: Incorporate user interactions immediately (clicks, likes, purchases)
3. **Cold Start Handling**: Recommend to new users with no history
4. **Diversity & Freshness**: Balance relevance with content diversity and recency
5. **Contextual Awareness**: Consider time, location, device, session context
6. **A/B Testing**: Support experimentation with different recommendation strategies
7. **Explainability** (optional): Provide reasons for recommendations

**Prioritize**: Focus on personalization, real-time updates, and scalability for MVP

### Non-Functional Requirements

1. **Low Latency**: <100ms for recommendation retrieval (P95)
2. **High Availability**: 99.99% uptime (recommendations are critical to user experience)
3. **Scalability**: Handle 1B+ users, 100M+ items
4. **Accuracy**: High CTR (Click-Through Rate), conversion rate, engagement metrics
5. **Freshness**: New items appear in recommendations within minutes
6. **Cost-Effective**: Balance model complexity with infrastructure costs

### Capacity Estimation

**Assumptions**:
- 1 Billion total users
- 200M Daily Active Users (DAU)
- 100 Million items in catalog
- Each user generates 50 interactions/day (views, clicks, likes, etc.)
- Recommendation requests: 500M/day (2.5 requests per DAU)

**Interaction Events**:
```
200M DAU × 50 interactions = 10B events/day
10B ÷ 100K seconds = 100K events/sec
Peak: ~200K events/sec
```

**Recommendation Requests**:
```
500M requests/day ÷ 100K seconds = 5,000 requests/sec
Peak: ~10,000 requests/sec
```

**Storage**:
```
User Profiles: 1B users × 10KB = 10TB
Item Catalog: 100M items × 5KB = 500GB
User-Item Interactions: 10B events/day × 365 days × 1 year × 200 bytes = 730TB/year
Model Parameters: Deep learning models ~10GB
Feature Store: User features (1B × 5KB) + Item features (100M × 5KB) = 5.5TB
Embeddings: Users (1B × 128 dims × 4 bytes) + Items (100M × 128 dims × 4 bytes) = 512GB + 51GB = 563GB
```

**Compute**:
```
Offline Training: 100+ GPU hours/day for deep learning models
Real-time Inference: 10K QPS × 10ms avg = 100 concurrent requests per server
Feature Engineering: Batch jobs processing 10B events/day
```

---

## Step 2: Architecture (20 min)

### High-Level Design

```
┌─────────────────────────────────────────────────────────────────┐
│                         CLIENT LAYER                            │
│  Web App | Mobile App | Smart TV | IoT Devices                  │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                      API GATEWAY / CDN                          │
│  Rate Limiting | Auth | Request Routing | Caching               │
└──────────────────────────┬──────────────────────────────────────┘
                           │
          ┌────────────────┼────────────────┐
          │                │                │
          ▼                ▼                ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ Recommendation│  │Event Tracking│  │  User        │
│   Service     │  │   Service    │  │  Service     │
│ (Inference)   │  │ (Behavioral) │  │ (Profiles)   │
└──────┬────────┘  └──────┬───────┘  └──────┬───────┘
       │                  │                  │
       │                  ▼                  │
       │         ┌────────────────┐          │
       │         │ Message Queue  │          │
       │         │ (Kafka/Kinesis)│          │
       │         └────────┬───────┘          │
       │                  │                  │
       ├──────────────────┼──────────────────┤
       │                  │                  │
       ▼                  ▼                  ▼
┌──────────────────────────────────────────────────┐
│              DATA & SERVING LAYER                │
├──────────────┬──────────────┬──────────────┬─────┤
│ Feature Store│ Model Serving│ Candidate Gen│Cache│
│  (Feast)     │ (TF Serving) │ (Vector DB)  │Redis│
└──────┬───────┴──────┬───────┴──────┬───────┴─────┘
       │              │              │
       ▼              ▼              ▼
┌──────────────────────────────────────────────────┐
│              STORAGE LAYER                       │
├──────────────┬──────────────┬───────────────────┤
│ PostgreSQL   │ Cassandra    │ S3 / Data Lake   │
│ (Metadata)   │(Interactions)│ (Historical Data)│
└──────────────┴──────────────┴───────────────────┘
       │
       ▼
┌──────────────────────────────────────────────────┐
│         ML PIPELINE (Offline Training)           │
├──────────────┬──────────────┬───────────────────┤
│ Data Prep    │ Model Training│ Model Evaluation │
│ (Spark/Beam) │ (TF/PyTorch)  │ (A/B Testing)    │
└──────────────┴──────────────┴───────────────────┘
```

### Key Components

**1. Recommendation Service (Real-time Inference)**:
- Serves top-N recommendations for user requests
- Two-stage ranking: Candidate generation → Ranking
- Latency: <100ms P95

**2. Candidate Generation**:
- Retrieves 100-1000 candidates from billions of items
- Uses approximate nearest neighbor (ANN) search
- Techniques: Collaborative filtering, content-based, vector similarity

**3. Ranking Service**:
- Ranks candidates using ML models (100-1000 items → top 10-50)
- Features: user profile, item features, context, historical interactions
- Models: Gradient Boosted Trees, Deep Neural Networks

**4. Feature Store**:
- Centralized storage for ML features
- Real-time + batch features
- Ensures training/serving consistency

**5. Event Tracking Service**:
- Captures user interactions (views, clicks, purchases, ratings)
- Streams to Kafka for real-time processing
- Stores in data lake for offline training

**6. ML Training Pipeline**:
- Offline: Batch training on historical data (daily/weekly)
- Online: Continuous learning from real-time feedback
- Experiment tracking and model versioning

---

## Step 3: Recommendation Algorithms (15 min)

### Algorithm 1: Collaborative Filtering (CF)

**User-Based CF**:
```python
def user_based_cf(user_id, k_neighbors=50, n_recommendations=10):
    """
    Find similar users, recommend items they liked
    """
    # 1. Find k most similar users based on interaction history
    similar_users = find_similar_users(user_id, k=k_neighbors)
    # Similarity: Cosine, Pearson correlation

    # 2. Get items liked by similar users
    candidate_items = get_items_from_users(similar_users)

    # 3. Score items by weighted sum of similarities
    scores = {}
    for item in candidate_items:
        scores[item] = sum(
            similarity(user_id, neighbor) * rating(neighbor, item)
            for neighbor in similar_users if has_rated(neighbor, item)
        )

    # 4. Return top N
    return top_n(scores, n_recommendations)
```

**Item-Based CF** (More scalable):
```python
def item_based_cf(user_id, n_recommendations=10):
    """
    Recommend items similar to what user liked
    """
    # 1. Get items user has interacted with
    user_items = get_user_history(user_id)

    # 2. Find similar items for each
    similar_items = []
    for item in user_items:
        similar_items.extend(find_similar_items(item, k=20))

    # 3. Score and rank
    scores = rank_by_similarity(similar_items)

    # 4. Filter out already interacted items
    return filter_and_return_top_n(scores, user_items, n_recommendations)
```

**Matrix Factorization** (Scalable CF):
```python
import numpy as np

class MatrixFactorization:
    """
    Factorize user-item matrix: R ≈ U × V^T
    U: user embeddings (users × latent_dims)
    V: item embeddings (items × latent_dims)
    """
    def __init__(self, n_users, n_items, latent_dim=128):
        self.U = np.random.normal(0, 0.1, (n_users, latent_dim))
        self.V = np.random.normal(0, 0.1, (n_items, latent_dim))

    def predict(self, user_id, item_id):
        """Predicted rating = dot product of embeddings"""
        return np.dot(self.U[user_id], self.V[item_id])

    def train(self, interactions, epochs=10, lr=0.01, reg=0.001):
        """SGD with L2 regularization"""
        for epoch in range(epochs):
            for user_id, item_id, rating in interactions:
                # Prediction error
                pred = self.predict(user_id, item_id)
                error = rating - pred

                # Gradient descent update
                self.U[user_id] += lr * (error * self.V[item_id] - reg * self.U[user_id])
                self.V[item_id] += lr * (error * self.U[user_id] - reg * self.V[item_id])

    def recommend(self, user_id, n=10):
        """Get top N items for user"""
        scores = self.U[user_id] @ self.V.T  # Dot product with all items
        return np.argsort(scores)[-n:][::-1]  # Top N indices
```

### Algorithm 2: Content-Based Filtering

```python
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

class ContentBasedRecommender:
    """
    Recommend items similar to user's past preferences
    """
    def __init__(self):
        self.vectorizer = TfidfVectorizer(max_features=5000)
        self.item_features = None

    def fit(self, items_metadata):
        """
        items_metadata: List of dicts with 'title', 'description', 'tags', etc.
        """
        # Combine text features
        texts = [
            f"{item['title']} {item['description']} {' '.join(item['tags'])}"
            for item in items_metadata
        ]

        # TF-IDF vectorization
        self.item_features = self.vectorizer.fit_transform(texts)

    def recommend(self, user_profile, n=10):
        """
        user_profile: Aggregated TF-IDF of items user liked
        """
        # Compute similarity between user profile and all items
        similarities = cosine_similarity(user_profile, self.item_features).flatten()

        # Top N similar items
        top_indices = np.argsort(similarities)[-n:][::-1]
        return top_indices, similarities[top_indices]
```

### Algorithm 3: Deep Learning - Two Tower Model

```python
import tensorflow as tf

class TwoTowerModel(tf.keras.Model):
    """
    Separate encoders for users and items
    Similarity = dot product of embeddings
    Used by YouTube, Pinterest, Airbnb
    """
    def __init__(self, n_users, n_items, embedding_dim=128):
        super().__init__()

        # User Tower
        self.user_embedding = tf.keras.layers.Embedding(n_users, 64)
        self.user_dense1 = tf.keras.layers.Dense(128, activation='relu')
        self.user_dense2 = tf.keras.layers.Dense(embedding_dim, activation='relu')

        # Item Tower
        self.item_embedding = tf.keras.layers.Embedding(n_items, 64)
        self.item_dense1 = tf.keras.layers.Dense(128, activation='relu')
        self.item_dense2 = tf.keras.layers.Dense(embedding_dim, activation='relu')

    def call(self, inputs):
        user_id = inputs['user_id']
        item_id = inputs['item_id']

        # User tower
        user_emb = self.user_embedding(user_id)
        user_vec = self.user_dense2(self.user_dense1(user_emb))

        # Item tower
        item_emb = self.item_embedding(item_id)
        item_vec = self.item_dense2(self.item_dense1(item_emb))

        # Dot product similarity
        return tf.reduce_sum(user_vec * item_vec, axis=1)

    def recommend(self, user_id, item_embeddings, k=10):
        """
        item_embeddings: Pre-computed embeddings for all items
        """
        user_vec = self.get_user_embedding(user_id)

        # Compute similarity with all items (batch matrix multiply)
        scores = tf.matmul(user_vec, item_embeddings, transpose_b=True)

        # Top K
        top_k_indices = tf.nn.top_k(scores, k=k).indices
        return top_k_indices
```

### Algorithm 4: Neural Collaborative Filtering (NCF)

```python
class NeuralCF(tf.keras.Model):
    """
    Combines matrix factorization with neural networks
    Paper: "Neural Collaborative Filtering" (He et al., 2017)
    """
    def __init__(self, n_users, n_items, embedding_dim=64, hidden_layers=[128, 64, 32]):
        super().__init__()

        # Embeddings
        self.user_embedding_gmf = tf.keras.layers.Embedding(n_users, embedding_dim)
        self.item_embedding_gmf = tf.keras.layers.Embedding(n_items, embedding_dim)
        self.user_embedding_mlp = tf.keras.layers.Embedding(n_users, embedding_dim)
        self.item_embedding_mlp = tf.keras.layers.Embedding(n_items, embedding_dim)

        # MLP layers
        self.mlp_layers = []
        for hidden_size in hidden_layers:
            self.mlp_layers.append(tf.keras.layers.Dense(hidden_size, activation='relu'))

        # Final prediction layer
        self.output_layer = tf.keras.layers.Dense(1, activation='sigmoid')

    def call(self, inputs):
        user_id = inputs['user_id']
        item_id = inputs['item_id']

        # GMF part (generalized matrix factorization)
        user_emb_gmf = self.user_embedding_gmf(user_id)
        item_emb_gmf = self.item_embedding_gmf(item_id)
        gmf_vector = user_emb_gmf * item_emb_gmf  # Element-wise product

        # MLP part
        user_emb_mlp = self.user_embedding_mlp(user_id)
        item_emb_mlp = self.item_embedding_mlp(item_id)
        mlp_vector = tf.concat([user_emb_mlp, item_emb_mlp], axis=-1)

        for layer in self.mlp_layers:
            mlp_vector = layer(mlp_vector)

        # Concatenate GMF and MLP
        concat_vector = tf.concat([gmf_vector, mlp_vector], axis=-1)

        # Final prediction
        return self.output_layer(concat_vector)
```

---

## Step 4: Two-Stage Recommendation Pipeline (15 min)

### Stage 1: Candidate Generation (Recall)

**Goal**: Retrieve 100-1000 relevant candidates from billions of items quickly

**Techniques**:

**1. Vector Similarity Search (ANN)**:
```python
import faiss  # Facebook AI Similarity Search

class CandidateGenerator:
    def __init__(self, item_embeddings, embedding_dim=128):
        """
        item_embeddings: numpy array (n_items × embedding_dim)
        """
        self.n_items = len(item_embeddings)
        self.embedding_dim = embedding_dim

        # Build FAISS index for fast ANN search
        self.index = faiss.IndexFlatIP(embedding_dim)  # Inner product

        # For large scale, use approximate methods
        # self.index = faiss.IndexIVFPQ(
        #     quantizer, embedding_dim, n_centroids=1000, m=8, nbits=8
        # )

        # Normalize embeddings for cosine similarity
        faiss.normalize_L2(item_embeddings)
        self.index.add(item_embeddings)

    def generate_candidates(self, user_embedding, k=500):
        """
        Retrieve top k items similar to user embedding
        """
        # Normalize user embedding
        user_embedding = user_embedding.reshape(1, -1)
        faiss.normalize_L2(user_embedding)

        # Search
        distances, indices = self.index.search(user_embedding, k)

        return indices[0], distances[0]  # item IDs and scores
```

**2. Multiple Candidate Sources**:
```python
def generate_candidates_multi_source(user_id, k_total=1000):
    """
    Combine candidates from different strategies
    """
    candidates = []

    # Source 1: Collaborative filtering (40% of candidates)
    cf_candidates = collaborative_filtering(user_id, k=400)
    candidates.extend(cf_candidates)

    # Source 2: Content-based (30%)
    content_candidates = content_based(user_id, k=300)
    candidates.extend(content_candidates)

    # Source 3: Trending/Popular (20%)
    trending_candidates = get_trending_items(k=200)
    candidates.extend(trending_candidates)

    # Source 4: Explore (new/diverse items) (10%)
    explore_candidates = get_explore_items(user_id, k=100)
    candidates.extend(explore_candidates)

    # Deduplicate
    return deduplicate(candidates)
```

### Stage 2: Ranking (Precision)

**Goal**: Rank 100-1000 candidates to select top 10-50 for display

**Features**:
```python
def extract_ranking_features(user_id, item_id, context):
    """
    Extract features for ranking model
    """
    features = {}

    # User features
    features['user_age'] = get_user_age(user_id)
    features['user_gender'] = get_user_gender(user_id)
    features['user_lifetime_purchases'] = get_purchase_count(user_id)
    features['user_avg_session_duration'] = get_avg_session_duration(user_id)
    features['user_embedding'] = get_user_embedding(user_id)  # 128-dim vector

    # Item features
    features['item_category'] = get_item_category(item_id)
    features['item_price'] = get_item_price(item_id)
    features['item_popularity'] = get_item_popularity(item_id)  # CTR, views
    features['item_age_days'] = get_item_age(item_id)  # Freshness
    features['item_embedding'] = get_item_embedding(item_id)  # 128-dim vector

    # User-Item interaction features
    features['user_item_affinity'] = compute_affinity(user_id, item_id)
    features['user_category_affinity'] = get_category_affinity(user_id, features['item_category'])
    features['similar_items_purchased'] = count_similar_purchases(user_id, item_id)

    # Contextual features
    features['time_of_day'] = context['hour']
    features['day_of_week'] = context['day_of_week']
    features['device_type'] = context['device']
    features['location_country'] = context['country']
    features['session_duration_so_far'] = context['session_duration']

    # Cross features
    features['user_category_match'] = user_category_match(user_id, item_id)

    return features
```

**Ranking Models**:

**Option 1: Gradient Boosted Trees (XGBoost/LightGBM)**:
```python
import lightgbm as lgb

class RankingModel:
    def __init__(self):
        self.model = None

    def train(self, training_data):
        """
        training_data: list of (user_id, item_id, label, features)
        label: 1 if clicked/purchased, 0 otherwise
        """
        X = [extract_ranking_features(u, i, ctx) for u, i, ctx in training_data]
        y = [label for _, _, label, _ in training_data]

        # LightGBM for ranking
        params = {
            'objective': 'lambdarank',  # Learning to rank
            'metric': 'ndcg',  # Normalized Discounted Cumulative Gain
            'learning_rate': 0.1,
            'num_leaves': 31,
            'feature_fraction': 0.8,
            'bagging_fraction': 0.8,
            'bagging_freq': 5,
        }

        train_data = lgb.Dataset(X, label=y)
        self.model = lgb.train(params, train_data, num_boost_round=100)

    def rank(self, user_id, candidate_items, context):
        """
        Rank candidate items for user
        """
        features = [
            extract_ranking_features(user_id, item_id, context)
            for item_id in candidate_items
        ]

        scores = self.model.predict(features)

        # Sort by score descending
        ranked_items = sorted(
            zip(candidate_items, scores),
            key=lambda x: x[1],
            reverse=True
        )

        return [item for item, score in ranked_items]
```

**Option 2: Deep Neural Network (DNN)**:
```python
class DeepRankingModel(tf.keras.Model):
    """
    Deep neural network for ranking
    Handles categorical + numerical + embedding features
    """
    def __init__(self, feature_config):
        super().__init__()

        # Embedding layers for categorical features
        self.embeddings = {}
        for cat_feature, vocab_size in feature_config['categorical'].items():
            self.embeddings[cat_feature] = tf.keras.layers.Embedding(
                vocab_size, embedding_dim=16
            )

        # Dense layers
        self.dense1 = tf.keras.layers.Dense(256, activation='relu')
        self.dropout1 = tf.keras.layers.Dropout(0.3)
        self.dense2 = tf.keras.layers.Dense(128, activation='relu')
        self.dropout2 = tf.keras.layers.Dropout(0.3)
        self.dense3 = tf.keras.layers.Dense(64, activation='relu')

        # Output layer
        self.output_layer = tf.keras.layers.Dense(1, activation='sigmoid')

    def call(self, inputs, training=False):
        # Process categorical features
        cat_embeddings = []
        for feature_name, embedding_layer in self.embeddings.items():
            cat_embeddings.append(embedding_layer(inputs[feature_name]))

        # Concatenate all features
        concat_features = tf.concat([
            *cat_embeddings,
            inputs['numerical_features'],
            inputs['user_embedding'],
            inputs['item_embedding']
        ], axis=-1)

        # Deep network
        x = self.dense1(concat_features)
        x = self.dropout1(x, training=training)
        x = self.dense2(x)
        x = self.dropout2(x, training=training)
        x = self.dense3(x)

        # Predict click probability
        return self.output_layer(x)
```

---

## Step 5: Data Pipeline & Feature Engineering (10 min)

### Real-time Feature Pipeline

```python
from kafka import KafkaConsumer, KafkaProducer
import redis

class RealTimeFeatureUpdater:
    """
    Updates user/item features in real-time from event stream
    """
    def __init__(self):
        self.consumer = KafkaConsumer(
            'user-interactions',
            bootstrap_servers=['kafka:9092'],
            value_deserializer=lambda m: json.loads(m.decode('utf-8'))
        )
        self.redis_client = redis.Redis(host='redis', port=6379)
        self.feature_store = FeatureStore()

    def process_events(self):
        """
        Process streaming events and update features
        """
        for message in self.consumer:
            event = message.value

            user_id = event['user_id']
            item_id = event['item_id']
            event_type = event['type']  # view, click, purchase, like
            timestamp = event['timestamp']

            # Update user features
            self.update_user_features(user_id, event)

            # Update item features
            self.update_item_features(item_id, event)

            # Update real-time counters
            self.update_realtime_counters(user_id, item_id, event_type)

    def update_user_features(self, user_id, event):
        """Update user profile in real-time"""
        # Increment interaction counts
        self.redis_client.hincrby(f"user:{user_id}", 'total_interactions', 1)
        self.redis_client.hincrby(f"user:{user_id}", f"{event['type']}_count", 1)

        # Update last activity timestamp
        self.redis_client.hset(f"user:{user_id}", 'last_active', event['timestamp'])

        # Update category preferences (with decay)
        category = self.get_item_category(event['item_id'])
        self.redis_client.zincrby(f"user:{user_id}:categories", 1.0, category)

    def update_item_features(self, item_id, event):
        """Update item popularity metrics"""
        # Increment view/click counters
        self.redis_client.hincrby(f"item:{item_id}", f"{event['type']}_count", 1)

        # Update trending score (time-decayed popularity)
        trending_key = f"trending:items"
        current_score = self.redis_client.zscore(trending_key, item_id) or 0

        # Exponential decay: newer interactions weighted more
        time_weight = math.exp(-0.001 * (time.time() - event['timestamp']))
        new_score = current_score * 0.99 + time_weight

        self.redis_client.zadd(trending_key, {item_id: new_score})
```

### Batch Feature Pipeline (Spark)

```python
from pyspark.sql import SparkSession
from pyspark.sql.functions import col, count, avg, sum, window

class BatchFeatureComputer:
    """
    Compute complex features using Spark batch processing
    Runs daily to update feature store
    """
    def __init__(self):
        self.spark = SparkSession.builder \
            .appName("RecSys Feature Engineering") \
            .getOrCreate()

    def compute_user_features(self, interactions_df):
        """
        Compute aggregated user features from historical interactions
        """
        user_features = interactions_df.groupBy("user_id").agg(
            count("*").alias("total_interactions"),
            count(col("event_type") == "purchase").alias("purchase_count"),
            count(col("event_type") == "click").alias("click_count"),
            avg("item_price").alias("avg_item_price"),
            avg("session_duration").alias("avg_session_duration")
        )

        # Compute categorical preferences
        category_prefs = interactions_df.groupBy("user_id", "category").agg(
            count("*").alias("category_count")
        )

        return user_features, category_prefs

    def compute_item_features(self, interactions_df):
        """
        Compute item popularity and engagement metrics
        """
        item_features = interactions_df.groupBy("item_id").agg(
            count("*").alias("total_views"),
            count(col("event_type") == "click").alias("click_count"),
            count(col("event_type") == "purchase").alias("purchase_count"),
            avg("rating").alias("avg_rating")
        )

        # Compute CTR (click-through rate)
        item_features = item_features.withColumn(
            "ctr",
            col("click_count") / col("total_views")
        )

        # Compute conversion rate
        item_features = item_features.withColumn(
            "conversion_rate",
            col("purchase_count") / col("click_count")
        )

        return item_features

    def compute_time_based_features(self, interactions_df):
        """
        Compute time-windowed features (last 7 days, last 30 days)
        """
        # Last 7 days activity
        last_7d = interactions_df.filter(
            col("timestamp") >= F.date_sub(F.current_date(), 7)
        ).groupBy("user_id").agg(
            count("*").alias("interactions_last_7d")
        )

        # Last 30 days activity
        last_30d = interactions_df.filter(
            col("timestamp") >= F.date_sub(F.current_date(), 30)
        ).groupBy("user_id").agg(
            count("*").alias("interactions_last_30d")
        )

        return last_7d, last_30d
```

---

## Step 6: Cold Start Problem (5 min)

### Strategy 1: New Users (User Cold Start)

```python
def handle_new_user_recommendations(user_id, user_signup_info):
    """
    Recommend to users with no interaction history
    """
    recommendations = []

    # 1. Popular items (40%)
    popular_items = get_trending_items(k=20)
    recommendations.extend(popular_items)

    # 2. Based on demographic/signup info (30%)
    if 'age' in user_signup_info and 'gender' in user_signup_info:
        demographic_items = get_demographic_based_items(
            age=user_signup_info['age'],
            gender=user_signup_info['gender'],
            k=15
        )
        recommendations.extend(demographic_items)

    # 3. Based on explicit preferences during onboarding (20%)
    if 'interests' in user_signup_info:
        interest_items = get_items_by_categories(
            categories=user_signup_info['interests'],
            k=10
        )
        recommendations.extend(interest_items)

    # 4. Exploration/diversity (10%)
    diverse_items = get_diverse_items(k=5)
    recommendations.extend(diverse_items)

    return deduplicate_and_rank(recommendations)
```

### Strategy 2: New Items (Item Cold Start)

```python
def handle_new_item_recommendations(item_id, item_metadata):
    """
    Get users who might be interested in new items
    """
    # 1. Content-based: Find users who liked similar items
    similar_items = find_similar_items_by_content(
        item_metadata,
        k=50
    )

    target_users = set()
    for similar_item in similar_items:
        users = get_users_who_liked(similar_item)
        target_users.update(users)

    # 2. Category-based: Users who engage with this category
    category_users = get_users_by_category_preference(
        category=item_metadata['category']
    )
    target_users.update(category_users)

    # 3. Early adopters: Users who frequently engage with new content
    early_adopters = get_early_adopter_users()
    target_users.update(early_adopters)

    return list(target_users)
```

---

## Step 7: Optimization & Advanced Topics (10 min)

### 1. Model Serving & Caching

```python
class RecommendationCache:
    """
    Multi-level caching for recommendations
    """
    def __init__(self):
        self.redis_client = redis.Redis()
        self.local_cache = {}  # In-memory LRU cache

    def get_recommendations(self, user_id, context):
        """
        Retrieve recommendations with caching
        """
        cache_key = f"recs:{user_id}:{context['device']}:{context['country']}"

        # Level 1: Local in-memory cache (fastest)
        if cache_key in self.local_cache:
            cached_recs, timestamp = self.local_cache[cache_key]
            if time.time() - timestamp < 60:  # 1 minute TTL
                return cached_recs

        # Level 2: Redis cache (fast)
        cached_recs = self.redis_client.get(cache_key)
        if cached_recs:
            recs = json.loads(cached_recs)
            self.local_cache[cache_key] = (recs, time.time())
            return recs

        # Level 3: Generate fresh recommendations (slow)
        recs = self.generate_recommendations(user_id, context)

        # Store in caches
        self.redis_client.setex(
            cache_key,
            time=300,  # 5 minutes TTL
            value=json.dumps(recs)
        )
        self.local_cache[cache_key] = (recs, time.time())

        return recs
```

### 2. A/B Testing & Experimentation

```python
class RecommendationExperiment:
    """
    A/B test different recommendation algorithms
    """
    def __init__(self):
        self.experiments = {
            'control': {'algorithm': 'matrix_factorization', 'weight': 0.5},
            'variant_a': {'algorithm': 'deep_neural_net', 'weight': 0.25},
            'variant_b': {'algorithm': 'two_tower_model', 'weight': 0.25}
        }

    def assign_variant(self, user_id):
        """
        Consistently assign user to experiment variant
        """
        # Hash user_id to ensure consistency
        hash_val = int(hashlib.md5(str(user_id).encode()).hexdigest(), 16)
        rand_val = (hash_val % 100) / 100.0  # 0.0 to 1.0

        cumulative_weight = 0.0
        for variant, config in self.experiments.items():
            cumulative_weight += config['weight']
            if rand_val < cumulative_weight:
                return variant

        return 'control'

    def get_recommendations(self, user_id, context):
        """
        Get recommendations based on assigned experiment variant
        """
        variant = self.assign_variant(user_id)
        algorithm = self.experiments[variant]['algorithm']

        # Log experiment assignment for analysis
        self.log_experiment_assignment(user_id, variant)

        # Generate recommendations using assigned algorithm
        if algorithm == 'matrix_factorization':
            return self.matrix_factorization_recommend(user_id)
        elif algorithm == 'deep_neural_net':
            return self.dnn_recommend(user_id)
        elif algorithm == 'two_tower_model':
            return self.two_tower_recommend(user_id)

    def analyze_experiment_results(self):
        """
        Compare metrics across variants
        """
        metrics = {}
        for variant in self.experiments.keys():
            variant_users = get_users_in_variant(variant)

            metrics[variant] = {
                'ctr': compute_ctr(variant_users),
                'conversion_rate': compute_conversion(variant_users),
                'engagement_time': compute_avg_engagement(variant_users),
                'revenue_per_user': compute_revenue(variant_users)
            }

        return metrics
```

### 3. Diversity & Exploration

```python
def diversify_recommendations(candidates, diversity_weight=0.3):
    """
    Balance relevance with diversity using MMR (Maximal Marginal Relevance)
    """
    selected = []
    remaining = candidates.copy()

    # Select first item (highest score)
    selected.append(remaining.pop(0))

    while len(selected) < 10 and remaining:
        best_score = -float('inf')
        best_idx = 0

        for idx, candidate in enumerate(remaining):
            # Relevance score
            relevance = candidate['score']

            # Diversity penalty: similarity to already selected items
            max_similarity = max(
                cosine_similarity(candidate['embedding'], s['embedding'])
                for s in selected
            )

            # MMR score: balance relevance and diversity
            mmr_score = (
                diversity_weight * relevance -
                (1 - diversity_weight) * max_similarity
            )

            if mmr_score > best_score:
                best_score = mmr_score
                best_idx = idx

        selected.append(remaining.pop(best_idx))

    return selected
```

### 4. Contextual Bandits (Exploration-Exploitation)

```python
class ContextualBandit:
    """
    Epsilon-greedy contextual bandit for balancing
    exploitation (best known items) vs exploration (trying new items)
    """
    def __init__(self, epsilon=0.1):
        self.epsilon = epsilon  # Exploration rate

    def select_recommendations(self, user_id, candidates, context):
        """
        Select items using epsilon-greedy strategy
        """
        n_recommendations = 10
        selected = []

        for i in range(n_recommendations):
            # Exploration: random item (10% of time)
            if random.random() < self.epsilon:
                # Explore: select random item from candidates
                explore_item = random.choice(candidates)
                selected.append(explore_item)
                self.log_exploration(user_id, explore_item, context)
            else:
                # Exploitation: select best item from model
                best_item = max(candidates, key=lambda x: x['score'])
                selected.append(best_item)

            # Remove selected item from candidates
            candidates.remove(selected[-1])

        return selected
```

---

## Step 8: Monitoring & Evaluation (5 min)

### Online Metrics

```python
class RecommenderMetrics:
    """
    Track recommendation system performance in production
    """
    def __init__(self):
        self.metrics_client = PrometheusClient()

    def track_online_metrics(self, user_id, recommendations, user_interactions):
        """
        Track real-time metrics
        """
        # 1. Click-Through Rate (CTR)
        clicks = sum(1 for item in recommendations if item in user_interactions['clicks'])
        ctr = clicks / len(recommendations)
        self.metrics_client.gauge('recommendation_ctr', ctr)

        # 2. Conversion Rate
        purchases = sum(1 for item in recommendations if item in user_interactions['purchases'])
        conversion_rate = purchases / len(recommendations)
        self.metrics_client.gauge('recommendation_conversion_rate', conversion_rate)

        # 3. Recommendation Latency
        latency_ms = measure_latency()
        self.metrics_client.histogram('recommendation_latency_ms', latency_ms)

        # 4. Coverage: % of catalog being recommended
        unique_items_recommended = len(set(recommendations))
        coverage = unique_items_recommended / total_catalog_size
        self.metrics_client.gauge('recommendation_coverage', coverage)

        # 5. Diversity: Intra-list diversity
        diversity = compute_diversity(recommendations)
        self.metrics_client.gauge('recommendation_diversity', diversity)

        # 6. Novelty: How many new/unseen items
        novel_items = sum(1 for item in recommendations
                         if item not in user_interactions['history'])
        novelty = novel_items / len(recommendations)
        self.metrics_client.gauge('recommendation_novelty', novelty)
```

### Offline Evaluation

```python
def offline_evaluation(model, test_data):
    """
    Evaluate model on held-out test set
    """
    metrics = {}

    # 1. Precision@K
    precisions = []
    for user_id, ground_truth in test_data:
        recommendations = model.recommend(user_id, k=10)
        relevant = set(ground_truth)
        recommended = set(recommendations)

        precision = len(relevant & recommended) / len(recommended)
        precisions.append(precision)

    metrics['precision@10'] = np.mean(precisions)

    # 2. Recall@K
    recalls = []
    for user_id, ground_truth in test_data:
        recommendations = model.recommend(user_id, k=10)
        relevant = set(ground_truth)
        recommended = set(recommendations)

        recall = len(relevant & recommended) / len(relevant) if relevant else 0
        recalls.append(recall)

    metrics['recall@10'] = np.mean(recalls)

    # 3. NDCG (Normalized Discounted Cumulative Gain)
    ndcgs = []
    for user_id, ground_truth_with_ratings in test_data:
        recommendations = model.recommend(user_id, k=10)
        ndcg = compute_ndcg(recommendations, ground_truth_with_ratings)
        ndcgs.append(ndcg)

    metrics['ndcg@10'] = np.mean(ndcgs)

    # 4. Mean Reciprocal Rank (MRR)
    mrrs = []
    for user_id, ground_truth in test_data:
        recommendations = model.recommend(user_id, k=10)

        # Find rank of first relevant item
        for rank, item in enumerate(recommendations, 1):
            if item in ground_truth:
                mrrs.append(1.0 / rank)
                break
        else:
            mrrs.append(0.0)

    metrics['mrr'] = np.mean(mrrs)

    return metrics
```

---

## Complete Recommendation System Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                         USER INTERACTION                            │
│  User views item → Click → Add to cart → Purchase → Rate            │
└────────────────────────┬────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       EVENT INGESTION                               │
│  Kafka/Kinesis ← Events (clicks, views, purchases, ratings)         │
└──────┬──────────────────────────────────────────────┬───────────────┘
       │                                              │
       │ (Real-time)                                  │ (Batch)
       ▼                                              ▼
┌──────────────────────┐                    ┌─────────────────────────┐
│  Real-time Feature   │                    │   Batch Feature Eng     │
│  Store (Redis)       │                    │   (Spark/Airflow)       │
│  • Session features  │                    │   • Aggregations        │
│  • Trending items    │                    │   • Historical features │
│  • User counters     │                    │   • Complex features    │
└──────┬───────────────┘                    └────────┬────────────────┘
       │                                              │
       └────────────────┬─────────────────────────────┘
                        │
                        ▼
              ┌──────────────────────┐
              │   Feature Store      │
              │   (Feast/Tecton)     │
              │  • User features     │
              │  • Item features     │
              │  • Context features  │
              └──────┬───────────────┘
                     │
         ┌───────────┼──────────────┐
         │           │              │
         ▼           ▼              ▼
    ┌────────┐  ┌────────┐    ┌──────────┐
    │Offline │  │Online  │    │Real-time │
    │Training│  │Training│    │Inference │
    │(Daily) │  │(Stream)│    │ Service  │
    └───┬────┘  └────┬───┘    └────┬─────┘
        │            │              │
        ▼            ▼              │
    ┌─────────────────────┐        │
    │   Model Registry    │        │
    │  • Versioning       │        │
    │  • A/B variants     │        │
    │  • Rollback support │        │
    └──────────┬──────────┘        │
               │                   │
               └─────────┬─────────┘
                         │
                         ▼
               ┌──────────────────────┐
               │  Candidate Generation │
               │  • CF vectors (FAISS) │
               │  • Content-based      │
               │  • Trending/Popular   │
               │  → 1000 candidates    │
               └──────────┬────────────┘
                          │
                          ▼
               ┌──────────────────────┐
               │   Ranking Service     │
               │  • Feature lookup     │
               │  • Model inference    │
               │  • Re-ranking         │
               │  → Top 10-50 items    │
               └──────────┬────────────┘
                          │
                          ▼
               ┌──────────────────────┐
               │  Post-processing      │
               │  • Diversity filter   │
               │  • Business rules     │
               │  • Deduplication      │
               │  • Personalization    │
               └──────────┬────────────┘
                          │
                          ▼
               ┌──────────────────────┐
               │   Cache Layer         │
               │  • Redis (user recs)  │
               │  • CDN (popular recs) │
               │  • TTL: 5-30 minutes  │
               └──────────┬────────────┘
                          │
                          ▼
               ┌──────────────────────┐
               │  Recommendation API   │
               │  Returns: Top N items │
               │  Latency: <100ms P95  │
               └──────────┬────────────┘
                          │
                          ▼
                 ┌─────────────────┐
                 │   User sees     │
                 │ Recommendations │
                 └─────────────────┘
```

---

## Interview Tips

**Questions to Ask**:
- What type of items are being recommended? (videos, products, articles, music?)
- What user signals are available? (explicit ratings, implicit clicks, time spent?)
- What's the scale? (users, items, interactions per day?)
- Real-time or batch recommendations?
- Cold start requirements? (how to handle new users/items?)
- Business constraints? (diversity, recency, promoted content?)

**Topics to Cover**:
- Two-stage pipeline (candidate generation + ranking)
- Multiple recommendation algorithms (collaborative, content-based, hybrid)
- Feature engineering and feature store
- Model training pipeline (offline + online)
- Caching strategy for low latency
- A/B testing infrastructure
- Monitoring and metrics (CTR, conversion, engagement)

**Common Follow-ups**:
- "How do you handle the cold start problem?"
  → Multiple strategies: popular items, demographic-based, onboarding preferences, explore/exploit
- "How do you ensure recommendations stay fresh?"
  → Real-time feature updates, time-decay in popularity, diversity filters
- "How would you scale to 1 billion users?"
  → Distributed training, model serving clusters, multi-level caching, sharding
- "How do you evaluate recommendation quality?"
  → Offline metrics (precision, recall, NDCG), online A/B tests (CTR, engagement, revenue)

---

## Key Takeaways

1. **Two-Stage Pipeline is Standard**: Candidate generation (recall) → Ranking (precision)
2. **Multiple Signals**: Combine collaborative filtering, content-based, popularity, context
3. **Feature Engineering is Critical**: 80% of model performance comes from good features
4. **Real-time + Batch**: Hybrid approach for freshness and scalability
5. **Cold Start Matters**: Have strategies for new users and new items
6. **Monitor Everything**: CTR, latency, diversity, coverage, revenue impact
7. **Experimentation Culture**: A/B test everything, iterate quickly
8. **Scale Considerations**: Use approximate methods (ANN), cache aggressively, shard data

## Further Reading

- "Recommender Systems Handbook" by Ricci et al.
- "Deep Learning for Recommender Systems" (YouTube, Netflix papers)
- "Two-Tower Models for Recommendations" (Google Research)
- TensorFlow Recommenders (TFRS) documentation
- Meta's DLRM (Deep Learning Recommendation Model)
- "Scaling Up Recommendations at Netflix" (Netflix Tech Blog)
- "RecSys Conference" proceedings (annual research conference)
