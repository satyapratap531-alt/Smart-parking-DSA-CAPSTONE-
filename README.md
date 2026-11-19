# Smart Parking Management System

A capstone project for Data Structures & Algorithms that manages and searches parking spaces efficiently using spatial data structures.

## Features
- **Quadtree** spatial partitioning to organise parking slots by location
- **Point search** to check a specific parking slot quickly
- **Orthogonal range search** to find all available slots within a rectangular area
- **slot booking & availability tracking
- A desktop user interface to interact with the system

## Tech Stack
- Language:Java 
- UI:Swing 

## How to Run
1. Clone the repo: `git clone https://github.com/satyapratap531-alt/Smart-parking-DSA-CAPSTONE-.git`
2. Open the `SmartParkingSystem` folder
3. Open DemoMain.java 
   Click Run

## Why Quadtrees?
A plain list needs a check of every slot for each search. A quadtree divides the area into regions, so range and point searches skip large parts of the map. This makes lookups much faster as the number of slots grows.

## Author
Tongala Satya Pratap Varma, B.Tech AIE, Amrita Vishwa Vidyapeetham
