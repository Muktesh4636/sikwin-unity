#!/usr/bin/env python3
"""Background worker — advances Colour Game rounds every second."""
import os
import sys
import time
import logging

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'dice_game.settings')

import django  # noqa: E402

django.setup()

from game.colour_engine import advance_colour_rounds  # noqa: E402

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [colour_worker] %(levelname)s %(message)s',
)
logger = logging.getLogger('colour_worker')


def main():
    logger.info('Colour round worker started')
    while True:
        try:
            rnd = advance_colour_rounds()
            if rnd:
                logger.debug('Active round %s status=%s', rnd.round_id, rnd.status)
        except Exception:
            logger.exception('Error advancing colour rounds')
        time.sleep(1)


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(0)
