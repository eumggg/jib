/** @type {import('jest').Config} */
module.exports = {
  preset: 'ts-jest',
  testEnvironment: 'node',
  roots: ['<rootDir>/src'],
  // Only the *.integration.test.ts files run in this config.
  testMatch: ['**/__tests__/**/*.integration.test.ts'],
  moduleFileExtensions: ['ts', 'js'],
  // Integration tests need PostGIS up; bump the per-test timeout so spatial
  // queries with EXPLAIN don't flake on slow CI.
  testTimeout: 30_000,
};
