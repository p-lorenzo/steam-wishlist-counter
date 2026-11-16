# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Steamworks developers monitoring wishlist performance for their own games. The initial user is the project owner; later distribution is intended through GitHub, F-Droid, and possibly Google Play.

## Product Purpose

A simple Android app to monitor per-game wishlists, explore date-filtered history, customize update notifications, and read a home-screen widget.

## Capabilities and Constraints

The personal prototype uses a user-entered Financial API key and manually entered AppIDs. No backend is required by the proposed personal experiment. Official support for direct mobile credential use remains unresolved; see docs/steamworks-api-findings.md. Reports are refreshed at least daily, not guaranteed real-time. Outstanding totals require validation, possibly a dated baseline. No actual credentials are needed by the UI study.

## Evidence on Hand

The repository contains a native Android personal prototype, API research, and four historical browser design studies. The browser studies use synthetic data; the native app starts empty and uses public Steam metadata and user-authorized financial reports. Public metadata was verified with AppID 620. No private financial reporting dataset has been supplied or verified yet.

## Stack

Native Kotlin / Jetpack Compose with Material 3, SQLite for cached reports, WorkManager for background synchronization, Android Keystore for financial-key encryption, and RemoteViews home-screen widgets. Minimum Android 8.0, current prototype target Android 15 / API 35. No Google Play Services dependency.

## Open Decisions

Public-release infrastructure, licensing, release signing, and final application ID remain undecided. The browser study is disposable plain HTML/CSS/JavaScript, separate from the native application. Public Steam store metadata is a best-effort integration with manual fallback, not a stable documented Steamworks contract.

## Confirmed Design Direction

The user selected Focus (direction C), with both light and dark appearance. Default to the device preference and allow a manual override. Apply the appearance consistently to screens and the widget preview.
