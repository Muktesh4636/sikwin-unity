from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):

    dependencies = [
        ("accounts", "0048_referral_rewards_system"),
    ]

    operations = [
        migrations.CreateModel(
            name="ClientEvent",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("event_type", models.CharField(db_index=True, max_length=16)),
                ("name", models.CharField(db_index=True, max_length=128)),
                ("message", models.TextField(blank=True, default="")),
                ("screen", models.CharField(blank=True, db_index=True, default="", max_length=64)),
                ("props", models.JSONField(blank=True, default=dict)),
                ("username", models.CharField(blank=True, default="", max_length=150)),
                ("device_model", models.CharField(blank=True, default="", max_length=128)),
                ("android_version", models.CharField(blank=True, default="", max_length=32)),
                ("app_version", models.CharField(blank=True, default="", max_length=32)),
                ("created_at", models.DateTimeField(auto_now_add=True, db_index=True)),
                (
                    "user",
                    models.ForeignKey(
                        blank=True,
                        null=True,
                        on_delete=django.db.models.deletion.SET_NULL,
                        related_name="client_events",
                        to="accounts.user",
                    ),
                ),
            ],
            options={
                "ordering": ["-created_at"],
            },
        ),
        migrations.AddIndex(
            model_name="clientevent",
            index=models.Index(fields=["event_type", "-created_at"], name="clientevt_type_created"),
        ),
        migrations.AddIndex(
            model_name="clientevent",
            index=models.Index(fields=["name", "-created_at"], name="clientevt_name_created"),
        ),
    ]
