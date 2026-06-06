package com.meetingminute.app.data.remote

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseClientProvider @Inject constructor() {

    val config = SupabaseConfig(
        url = "https://fxzddkwvhavwvzttdlnj.supabase.co",
        anonKey = "sb_publishable_wUgKgyaWW8uQvHwWSylSAA_HujDKOra"
    )
}
