/** @type {import('jest').Config} */
module.exports = {
  preset: 'ts-jest',
  testEnvironment: 'node',
  roots: ['<rootDir>/src'],
  // Exclude integration tests from the default unit-test run.
  testMatch: ['**/__tests__/**/*.test.ts'],
  testPathIgnorePatterns: ['\\.integration\\.test\\.ts$'],
  moduleFileExtensions: ['ts', 'js'],
  collectCoverageFrom: ['src/**/*.ts', '!src/**/*.d.ts'],
};
