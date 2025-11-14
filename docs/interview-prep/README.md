# Interview Preparation

## Contents

1. [Interview Framework (RADIO)](framework.md) - Structured approach to system design interviews
2. [Common Questions & Answers](common-questions.md) - Frequently asked questions
3. [Estimation & Capacity Planning](capacity-planning.md) - Back-of-the-envelope calculations
4. [Behavioral Questions](behavioral.md) - Leadership and experience questions
5. [Architecture Review](architecture-review.md) - Discussing past systems

## Interview Process Overview

### Typical Interview Loop

**Phone Screen** (45-60 min):
- Resume discussion
- Basic technical questions
- Coding or simple system design

**Onsite/Virtual** (4-6 hours):
1. **System Design** (1-2 rounds, 45-60 min each)
2. **Coding** (1-2 rounds)
3. **Behavioral/Leadership** (1 round)
4. **Architecture Review** (1 round)

### What Interviewers Assess

**Technical Competence**:
- Breadth of knowledge (know many technologies)
- Depth of knowledge (deep expertise in some areas)
- Trade-off analysis
- Practical experience

**Problem Solving**:
- Requirements gathering
- Breaking down complex problems
- Systematic approach
- Handling ambiguity

**Communication**:
- Clearly explain complex concepts
- Ask clarifying questions
- Listen and adapt
- Collaborate with interviewer

**Experience**:
- Real-world battle scars
- Learning from failures
- Scaling challenges solved
- Team leadership

## Study Plan (6 Weeks)

### Week 1-2: Fundamentals
- Core concepts (scalability, availability, CAP)
- Distributed systems basics
- Database fundamentals
- Networking basics

**Study**: 2-3 hours/day
**Practice**: None yet (build foundation)

### Week 3-4: Deep Dives
- Distributed systems (consensus, replication, partitioning)
- Scalability patterns (caching, load balancing)
- Platform engineering (K8s, observability)
- Real-world architectures (read engineering blogs)

**Study**: 2 hours/day
**Practice**: 30 min/day (design simple systems)

### Week 5-6: Practice
- System design examples (10+ systems)
- Mock interviews (3-5)
- Review weak areas
- Behavioral question prep

**Study**: 1 hour/day
**Practice**: 2 hours/day

### Final Week: Polish
- Review notes
- Practice explanations out loud
- Mock interview with peer
- Behavioral stories prepared

## Resources

### Books
- "Designing Data-Intensive Applications" by Martin Kleppmann
- "System Design Interview" by Alex Xu (Volumes 1 & 2)
- "Web Scalability for Startup Engineers" by Artur Ejsmont

### Engineering Blogs
- Netflix Tech Blog
- Uber Engineering Blog
- Airbnb Engineering
- LinkedIn Engineering
- AWS Architecture Blog

### Practice Platforms
- LeetCode (system design section)
- Pramp (mock interviews)
- SystemsExpert
- Educative.io

### YouTube Channels
- Gaurav Sen
- Tech Dummies
- ByteByteGo

## Interview Tips

### Before the Interview

1. **Research the Company**:
   - What systems they build
   - Tech stack they use
   - Engineering blog posts
   - Recent launches

2. **Prepare Questions**:
   - Ask about their architecture
   - Team structure
   - Technical challenges
   - Tech stack

3. **Setup**:
   - Test video/audio
   - Quiet environment
   - Whiteboard or digital drawing tool
   - Water nearby

### During the Interview

**Do**:
- ✓ Ask clarifying questions
- ✓ State assumptions clearly
- ✓ Think out loud
- ✓ Discuss trade-offs
- ✓ Draw diagrams
- ✓ Check in with interviewer
- ✓ Be collaborative

**Don't**:
- ✗ Jump to solution immediately
- ✗ Over-engineer
- ✗ Ignore constraints
- ✗ Be defensive about feedback
- ✗ Stay silent
- ✗ Dismiss edge cases

### Common Mistakes

**1. Not Asking Questions**:
```
Bad: "I'll design Twitter" (without understanding requirements)
Good: "How many users? Read/write ratio? Key features?"
```

**2. Over-Engineering**:
```
Bad: "We'll use blockchain and ML for this..."
Good: "Let's start simple, then scale as needed"
```

**3. Ignoring Trade-offs**:
```
Bad: "We'll use microservices"
Good: "Microservices give us X benefit but cost Y complexity"
```

**4. No Numbers**:
```
Bad: "We need a database"
Good: "With 10M users, 100 writes/sec, we need..."
```

**5. Jumping to Implementation**:
```
Bad: Immediately coding APIs
Good: High-level architecture first, then deep dive
```

## Red Flags to Avoid

- Arguing with interviewer
- Not listening to hints
- Claiming expertise you don't have
- Bad-mouthing previous employers
- No questions for interviewer
- Overconfidence or arrogance
- Not admitting when you don't know

## What to Do When Stuck

1. **Clarify**: Ask for more details
2. **Simplify**: Start with simpler version
3. **Example**: Use specific example to reason through
4. **Think Aloud**: Share your thought process
5. **Ask**: "What aspect should I focus on?"

## Post-Interview

**Send Thank You**:
- Within 24 hours
- Mention specific discussion points
- Reiterate interest

**Reflect**:
- What went well?
- What could improve?
- Topics to review?

**Prepare for Next**:
- Apply learnings
- Study weak areas
- Practice more

## Confidence Builders

**You've Got This If**:
- ✓ Designed/built production systems
- ✓ Understand trade-offs
- ✓ Can explain your past work clearly
- ✓ Stay calm under pressure
- ✓ Learn from feedback

**Remember**:
- Interviewers want you to succeed
- It's a conversation, not an interrogation
- Imperfect answers are okay
- Showing thought process > perfect solution
