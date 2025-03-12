# Set Card Game

## Overview
The **Set Card Game** is a multi-threaded implementation of the classic Set game using Java concurrency and synchronization techniques. Players compete to find valid sets of three cards based on their features while the game dynamically updates the deck and enforces penalties for incorrect selections.

## Features
- Supports both **human and AI players**
- **Concurrency and synchronization** to handle multiple players simultaneously
- **Graphical User Interface (GUI)** for real-time gameplay interaction
- **Automated dealer mechanics** for shuffling, dealing, and validating sets
- **Keyboard-based input system** for fast-paced gameplay
- **Configurable settings** via a `config.properties` file

## Game Rules
1. The game starts with **12 cards** placed in a 3x4 grid.
2. Each card has **four features**:
   - **Color**: Red, Green, or Purple
   - **Number**: 1, 2, or 3 shapes
   - **Shape**: Squiggle, Diamond, or Oval
   - **Shading**: Solid, Partial, or Empty
3. Players must find **three cards** that form a **valid set**:
   - Each feature must be **either all the same or all different** across the three cards.
4. If a player finds a correct set:
   - The set is removed from the board and replaced with new cards.
   - The player gains a point but is frozen for a short period.
5. If a player selects an incorrect set:
   - The player is penalized and frozen for a longer duration.
6. If no sets are available on the board, the dealer reshuffles the deck.
7. The game ends when there are no possible sets remaining.
8. The player with the most points wins.

## Game Components
### 1. **Cards & Features**
Each card is represented by an integer (0-80) and mapped to a unique set of features using:
```
30 * f1 + 31 * f2 + 32 * f3 + 33 * f4
```

### 2. **The Table**
- A shared data structure for players and the dealer.
- Holds the **3x4 card grid** and manages token placements.

### 3. **Players (Human & AI)**
- Each player runs on a separate thread.
- **Human players** use keyboard input.
- **AI players** simulate key presses.
- Each player maintains an action queue (size = 3) to store selected cards.

### 4. **The Dealer**
- The dealer runs on a single thread and controls game flow:
  - **Manages player threads**
  - **Deals and shuffles cards**
  - **Checks for valid sets**
  - **Keeps track of the game timer**
  - **Assigns penalties and points**
  - **Determines the winner**

### 5. **Graphical User Interface (GUI)**
- Displays the game board, countdown timer, and player scores.
- Handled via the `UserInterface` and `UserInterfaceImpl` classes.

### 6. **Keyboard Input Handling**
- Uses `InputManager` to map key presses to board slots.
- Players interact with the game using a **predefined keyboard layout**.

## Installation & Execution
### Prerequisites
- **Java 11+**
- **Apache Maven**

### Build & Run
Navigate to the project root directory and run the following commands:

#### Compile:
```sh
mvn compile
```

#### Run:
```sh
mvn exec:java
```

#### Full Build & Run:
```sh
mvn clean compile exec:java
```

## Configuration
Modify `config.properties` to adjust gameplay settings such as:
- Number of human/AI players
- Penalty durations
- Turn timeout

## Author
- **Din Yair Sadot**

## License
This project is for educational purposes and follows BGU course assignment guidelines.

