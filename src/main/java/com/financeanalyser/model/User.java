package com.financeanalyser.model;

public class User {
    private int userId;
    private String username;
    private String passwordHash;
    private double monthlySalary;
    private String createdAt;
    private String profilePicPath;

    public User(int userId, String username, String passwordHash,
                double monthlySalary, String createdAt, String profilePicPath) {
        this.userId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
        this.monthlySalary = monthlySalary;
        this.createdAt = createdAt;
        this.profilePicPath = profilePicPath;
    }

    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public double getMonthlySalary() { return monthlySalary; }
    public String getCreatedAt() { return createdAt; }
    public String getProfilePicPath() { return profilePicPath; }

    public void setMonthlySalary(double monthlySalary) { this.monthlySalary = monthlySalary; }
}
