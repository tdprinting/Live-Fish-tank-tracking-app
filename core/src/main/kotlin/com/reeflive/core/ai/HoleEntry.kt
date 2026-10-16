package com.reeflive.core.ai

/**
 * A blenny goes home tail first (owner direction 2026-10-14; research 10 §7 "head-out shelter inspection", video
 * fish_tailspot_blenny "hole-peeking idle"). The sequence (swim to a stand-off point in front of the mouth, pivot to
 * face out, scull backwards into the tunnel along its axis, rest head-out) lives in [InHole], which works on the
 * measured holes of the rock (sim/RockHoles.kt, SIMULATION §24) and holds the single-occupant claim on the hole.
 */
typealias TailFirstHole = InHole
