# Smart Parking Management System

A capstone project for Data Structures & Algorithms that manages and searches parking spaces efficiently using spatial data structures.

## Features
- **Quadtree** spatial partitioning to organise parking slots by location
- **Point search** to check a specific parking slot quickly
- **Orthogonal range search** to find all available slots within a rectangular area
- **Nearest vacant slot** recommendation for a driver's location
- **Multi-floor support** with a separate quadtree for each floor
- **Slot booking and availability tracking** with reservation hold times
- Interactive **Swing desktop UI** with a live map and activity log

## Demo
- <img width="1917" height="1198" alt="Screenshot 2026-11-19 230142" src="https://github.com/user-attachments/assets/65946b81-baa1-4d57-8d30-988c61112e60" />

## Tech Stack
- Language: Java 
- UI: Swing 

## How to Run
1. Clone the repo: `git clone https://github.com/satyapratap531-alt/Smart-parking-DSA-CAPSTONE-.git`
2. Open the `SmartParkingSystem` folder
3. Open DemoMain.java in your IDE (IntelliJ, Eclipse or VS Code)
   Click Run

## Why Quadtrees?
A plain list needs a check of every slot for each search. A quadtree divides the area into regions, so range and point searches skip large parts of the map. This makes lookups much faster as the number of slots grows.

## Author
Tongala Satya Pratap Varma, B.Tech AIE, Amrita Vishwa Vidyapeetham
